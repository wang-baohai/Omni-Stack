SET NAMES utf8mb4;

-- Fix garbled approval comments (PowerShell UTF-8 encoding issue)

-- Instance 1 (office-low): admin approved "同意采购"
UPDATE ACT_HI_COMMENT
SET MESSAGE_ = '同意采购', USER_ID_ = '1'
WHERE ID_ = '6347021f-8af3-11f1-b230-d23947eb06c7';

-- Instance 3 (raw-low): admin approved "材料补货审批通过"
UPDATE ACT_HI_COMMENT
SET MESSAGE_ = '材料补货审批通过', USER_ID_ = '1'
WHERE ID_ = '6368e208-8af3-11f1-b230-d23947eb06c7';

-- Instance 4 (it-high): admin approved dept level "部门审批通过"
UPDATE ACT_HI_COMMENT
SET MESSAGE_ = '部门审批通过', USER_ID_ = '1'
WHERE ID_ = '63840b31-8af3-11f1-b230-d23947eb06c7';

-- Instance 5 (office-high): admin approved admin level "行政审批通过"
UPDATE ACT_HI_COMMENT
SET MESSAGE_ = '行政审批通过', USER_ID_ = '1'
WHERE ID_ = '6462ab7b-8af3-11f1-b230-d23947eb06c7';

-- Instance 4 (it-high): add missing CTO approval comments
-- CTO task 1: user 107
INSERT INTO ACT_HI_COMMENT (ID_, TYPE_, TIME_, USER_ID_, TASK_ID_, PROC_INST_ID_, ACTION_, MESSAGE_)
VALUES (
  'a1b2c3d4-8af3-11f1-b230-d23947eb06c7',
  'comment',
  '2026-07-29 10:26:11.000',
  '107',
  '638ce4e3-8af3-11f1-b230-d23947eb06c7',
  '7416dcfe-8af2-11f1-b230-d23947eb06c7',
  'AddComment',
  'CTO审批通过'
);

-- CTO task 2: user 108
INSERT INTO ACT_HI_COMMENT (ID_, TYPE_, TIME_, USER_ID_, TASK_ID_, PROC_INST_ID_, ACTION_, MESSAGE_)
VALUES (
  'a1b2c3d5-8af3-11f1-b230-d23947eb06c7',
  'comment',
  '2026-07-29 10:26:11.000',
  '108',
  '638d0bf8-8af3-11f1-b230-d23947eb06c7',
  '7416dcfe-8af2-11f1-b230-d23947eb06c7',
  'AddComment',
  'CTO审批通过'
);
