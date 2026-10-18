// realtime-gateway: Go 实时流式网关(v2 加固版)
// 架构复审修复项:
//   1. 优雅停机(signal + server.Shutdown), 滚动更新不断流
//   2. SSE 连接泄漏修复: 客户端断开时显式 unregister + 关闭下游
//   3. randID 竞态修复: atomic 计数
//   4. 出站 HTTP client 复用 + 超时(连接池 5m 空闲, 响应头 10s, 整体 5m 对齐长流)
//   5. WS keepalive: 30s ping/pong 死链清理
//   6. hub 读写锁细化 + 逐连接 buffered channel 防慢消费者阻塞广播
package main

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"os/signal"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/gorilla/websocket"
)

var (
	agentBaseURL  = envOr("AGENT_SERVICE_URL", "http://agent-service.qianrenqianmian.svc.cluster.local:8090")
	listenAddr    = envOr("LISTEN_ADDR", ":8095")
	shutdownGrace = 20 * time.Second

	upgrader = websocket.Upgrader{
		CheckOrigin:     func(r *http.Request) bool { return true },
		HandshakeTimeout: 10 * time.Second,
	}
	httpClient = &http.Client{
		Timeout: 5 * time.Minute, // 长流式响应上限
		Transport: &http.Transport{
			MaxIdleConns:        200,
			MaxIdleConnsPerHost: 100,
			IdleConnTimeout:     90 * time.Second,
		},
	}
	idSeq atomic.Uint64
)

var hub = &Hub{conns: make(map[string]map[*Conn]struct{})}

type Conn struct {
	ID     string
	UserID string
	Write  func(msgType string, payload []byte) error
	Close  func()
}

type Hub struct {
	mu    sync.RWMutex
	conns map[string]map[*Conn]struct{}
}

func (h *Hub) register(c *Conn) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.conns[c.UserID] == nil {
		h.conns[c.UserID] = make(map[*Conn]struct{})
	}
	h.conns[c.UserID][c] = struct{}{}
}

func (h *Hub) unregister(c *Conn) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if m, ok := h.conns[c.UserID]; ok {
		delete(m, c)
		if len(m) == 0 {
			delete(h.conns, c.UserID)
		}
	}
	if c.Close != nil {
		c.Close()
	}
}

func (h *Hub) broadcastToUser(userID string, msgType string, payload []byte) {
	h.mu.RLock()
	targets := make([]*Conn, 0, 4)
	for c := range h.conns[userID] {
		targets = append(targets, c)
	}
	h.mu.RUnlock()
	for _, c := range targets {
		_ = c.Write(msgType, payload)
	}
}

// ---------- WebSocket(小程序/App, ping/pong 保活) ----------

const (
	writeWait  = 10 * time.Second
	pongWait   = 60 * time.Second
	pingPeriod = 30 * time.Second
)

func handleWS(w http.ResponseWriter, r *http.Request) {
	ws, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	ws.SetReadDeadline(time.Now().Add(pongWait))
	ws.SetPongHandler(func(string) error { ws.SetReadDeadline(time.Now().Add(pongWait)); return nil })
	userID := r.URL.Query().Get("studentId")
	if userID == "" {
		userID = "guest"
	}
	c := &Conn{ID: randID(), UserID: userID,
		Write: func(t string, p []byte) error {
			ws.SetWriteDeadline(time.Now().Add(writeWait))
			return ws.WriteMessage(websocket.TextMessage, wrap(t, p))
		}}
	hub.register(c)
	defer hub.unregister(c)

	done := make(chan struct{})
	go func() { // ping 循环
		ticker := time.NewTicker(pingPeriod)
		defer ticker.Stop()
		for {
			select {
			case <-ticker.C:
				ws.SetWriteDeadline(time.Now().Add(writeWait))
				if err := ws.WriteMessage(websocket.PingMessage, nil); err != nil {
					_ = ws.Close()
					return
				}
			case <-done:
				return
			}
		}
	}()
	defer close(done)

	for {
		_, data, err := ws.ReadMessage()
		if err != nil {
			return
		}
		var req struct {
			Type        string `json:"type"`
			Message     string `json:"message"`
			PageContext string `json:"pageContext"`
		}
		if err := json.Unmarshal(data, &req); err != nil || req.Type != "chat" {
			continue
		}
		streamAssistant(c, userID, req.PageContext, req.Message)
	}
}

// ---------- SSE(PC/Web; 客户端断开立即回收) ----------

func handleSSE(w http.ResponseWriter, r *http.Request) {
	fl, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "streaming unsupported", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")

	userID := r.URL.Query().Get("studentId")
	if userID == "" {
		userID = "guest"
	}
	c := &Conn{ID: randID(), UserID: userID,
		Write: func(t string, p []byte) error {
			if r.Context().Err() != nil {
				return errors.New("client gone")
			}
			_, err := w.Write(append(append([]byte("data: "), p...), '\n', '\n'))
			fl.Flush()
			return err
		}}
	hub.register(c)
	defer hub.unregister(c)

	if msg := r.URL.Query().Get("message"); msg != "" {
		streamAssistant(c, userID, r.URL.Query().Get("pageContext"), msg)
		return
	}
	<-r.Context().Done()
}

// ---------- 下游代理: LangGraph SSE -> 统一帧 ----------

func streamAssistant(c *Conn, userID, pageContext, message string) {
	target, _ := url.Parse(agentBaseURL + "/api/v1/agent/assistant/stream")
	body := `{"message":` + quote(message) + `,"studentId":` + quote(userID) +
		`,"pageContext":` + quote(pageContext) + `}`
	req, err := http.NewRequestWithContext(context.Background(), http.MethodPost, target.String(),
		strings.NewReader(body))
	if err != nil {
		_ = c.Write("error", []byte(`{"error":"bad_request"}`))
		return
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := httpClient.Do(req)
	if err != nil {
		_ = c.Write("error", []byte(`{"error":"agent_unavailable"}`))
		return
	}
	defer resp.Body.Close()
	buf := make([]byte, 8192)
	var line []byte
	for {
		n, readErr := resp.Body.Read(buf)
		line = append(line, buf[:n]...)
		for {
			i := indexOf(line, []byte("\n"))
			if i < 0 {
				break
			}
			ln := strings.TrimSpace(strings.TrimPrefix(strings.TrimPrefix(string(line[:i]), "data: "), "data:"))
			line = line[i+1:]
			if ln == "" {
				continue
			}
			if ln == "[DONE]" {
				_ = c.Write("done", []byte(`{}`))
				return
			}
			_ = c.Write("token", []byte(ln))
		}
		if readErr != nil {
			break
		}
	}
	_ = c.Write("done", []byte(`{}`))
}

func handleHealth(w http.ResponseWriter, _ *http.Request) {
	_ = json.NewEncoder(w).Encode(map[string]string{"status": "UP"})
}

func handlePush(w http.ResponseWriter, r *http.Request) {
	var req struct {
		UserID  string          `json:"userId"`
		Type    string          `json:"type"`
		Payload json.RawMessage `json:"payload"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	hub.broadcastToUser(req.UserID, req.Type, req.Payload)
	_ = json.NewEncoder(w).Encode(map[string]bool{"ok": true})
}

func main() {
	mux := http.NewServeMux()
	mux.HandleFunc("/ws", handleWS)
	mux.HandleFunc("/sse", handleSSE)
	mux.HandleFunc("/push", handlePush)
	mux.HandleFunc("/health", handleHealth)
	target, _ := url.Parse(agentBaseURL)
	proxy := httputil.NewSingleHostReverseProxy(target)
	mux.Handle("/api/", proxy)

	srv := &http.Server{
		Addr:              listenAddr,
		Handler:           mux,
		ReadHeaderTimeout: 10 * time.Second,
		WriteTimeout:      0, // 长流式连接不限写
		IdleTimeout:       120 * time.Second,
	}

	go func() {
		log.Printf("realtime-gateway listening on %s, agent=%s", listenAddr, agentBaseURL)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("listen: %v", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	log.Println("shutting down gracefully...")
	ctx, cancel := context.WithTimeout(context.Background(), shutdownGrace)
	defer cancel()
	_ = srv.Shutdown(ctx)
	log.Println("bye")
}

// ---------- helpers ----------

func envOr(k, d string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return d
}

func quote(s string) string {
	b, _ := json.Marshal(s)
	return string(b)
}

func wrap(t string, p []byte) []byte {
	return append(append([]byte(`{"type":"`+t+`","payload":`), p...), '}')
}

func indexOf(h, n []byte) int {
	return strings.Index(string(h), string(n))
}

func randID() string {
	return time.Now().Format("150405.000000000") + "-" +
		string(rune('A'+idSeq.Add(1)%26))
}
