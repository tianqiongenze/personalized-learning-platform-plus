---
name: vocational-exercise-generation
description: 生成高职练习题组, 覆盖常开/常闭触点等知识点, 结合重庆33618产业情境, 遵循三档难度梯度
user-invocable: true
bloom-levels: remember,understand,apply
capabilities: [kt_mastery, course_search]
---

# 高职练习题组生成 SOP

## Core Rules
1. 必须先调用 kt_mastery 获取学生当前掌握度, 按掌握度选择难度档:
   - mastery < 0.4 → 基础题为主 (70%基础/30%进阶)
   - 0.4 ≤ mastery < 0.7 → 梯度题 (30/50/20)
   - mastery ≥ 0.7 → 挑战题为主 (20/40/40)
2. 每道题必须包含: 题干、考查知识点、答案、(可选)解题思路
3. 至少 60% 的题目嵌入 33618 产业情境(智能网联新能源汽车/智能制造产线/新一代电子信息)
4. 输出 JSON 数组, 每题一个对象

## Response Style
- 题干简洁明确, 不用"保证""绝对正确"等禁用词
- 产业情境要具体到产线/工序/设备, 不写空泛的"在工厂中"
