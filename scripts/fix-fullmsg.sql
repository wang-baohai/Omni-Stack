SET NAMES utf8mb4;

-- Fix FULL_MSG_ column (BLOB) - this is what Comment.getFullMessage() reads
-- The MESSAGE_ column was already fixed, copy correct data to FULL_MSG_

-- Update existing records: copy MESSAGE_ to FULL_MSG_
UPDATE ACT_HI_COMMENT
SET FULL_MSG_ = CAST(MESSAGE_ AS BINARY)
WHERE FULL_MSG_ IS NOT NULL AND MESSAGE_ IS NOT NULL;

-- Fix NULL FULL_MSG_ for CTO approval records (inserted earlier without FULL_MSG_)
UPDATE ACT_HI_COMMENT
SET FULL_MSG_ = CAST(MESSAGE_ AS BINARY)
WHERE FULL_MSG_ IS NULL AND MESSAGE_ IS NOT NULL;
