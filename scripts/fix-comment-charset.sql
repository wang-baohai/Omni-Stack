SET NAMES utf8mb4;

-- Fix ACT_HI_COMMENT.MESSAGE_ column charset
-- Current: utf8mb3_bin → JDBC reads bytes as Latin-1
-- Target: utf8mb4_unicode_ci → JDBC properly interprets as UTF-8

ALTER TABLE ACT_HI_COMMENT
  MODIFY COLUMN MESSAGE_ varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
