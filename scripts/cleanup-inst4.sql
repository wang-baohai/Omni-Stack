-- Cleanup Instance 4 (7416dcfe-8af2-11f1-b230-d23947eb06c7) residual runtime data
-- This is needed due to Flowable 8.0.0 MI variable scoping edge case

SET @pid = '7416dcfe-8af2-11f1-b230-d23947eb06c7';

-- 1. Delete runtime tasks
DELETE FROM ACT_RU_TASK WHERE PROC_INST_ID_ = @pid;

-- 2. Delete runtime variables
DELETE FROM ACT_RU_VARIABLE WHERE PROC_INST_ID_ = @pid;

-- 3. Delete runtime executions (children first, then root)
DELETE FROM ACT_RU_EXECUTION WHERE PROC_INST_ID_ = @pid AND PARENT_ID_ IS NOT NULL;
DELETE FROM ACT_RU_EXECUTION WHERE PROC_INST_ID_ = @pid AND PARENT_ID_ IS NULL;

-- 4. Update historical process instance to mark as completed
UPDATE ACT_HI_PROCINST 
SET END_TIME_ = NOW(), 
    DURATION_ = TIMESTAMPDIFF(MICROSECOND, START_TIME_, NOW()) / 1000,
    DELETE_REASON_ = 'completed'
WHERE ID_ = @pid;

-- 5. Update historical tasks to mark as completed
UPDATE ACT_HI_TASKINST 
SET END_TIME_ = NOW(),
    DURATION_ = TIMESTAMPDIFF(MICROSECOND, START_TIME_, NOW()) / 1000,
    DELETE_REASON_ = 'completed'
WHERE PROC_INST_ID_ = @pid AND END_TIME_ IS NULL;

-- 6. Update wf_process_instance_ext to mark as approved
UPDATE wf_process_instance_ext 
SET status = 2, 
    completion_result = 'APPROVED',
    end_time = NOW()
WHERE process_instance_id = @pid;

-- Verify
SELECT 'ACT_RU_TASK' as tbl, COUNT(*) as cnt FROM ACT_RU_TASK WHERE PROC_INST_ID_ = @pid
UNION ALL
SELECT 'ACT_RU_EXECUTION', COUNT(*) FROM ACT_RU_EXECUTION WHERE PROC_INST_ID_ = @pid
UNION ALL
SELECT 'ACT_RU_VARIABLE', COUNT(*) FROM ACT_RU_VARIABLE WHERE PROC_INST_ID_ = @pid;

SELECT status, completion_result FROM wf_process_instance_ext WHERE process_instance_id = @pid;
SELECT ID_, END_TIME_ IS NOT NULL as is_completed FROM ACT_HI_PROCINST WHERE ID_ = @pid;
