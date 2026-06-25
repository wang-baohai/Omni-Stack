package com.omni.base.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工作台任务统计 DTO。
 *
 * @author Omni-Stack Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyJobStats implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务总数 */
    private long totalJobs;

    /** 今日执行次数 */
    private long todayExecuted;

    /** 今日失败次数 */
    private long todayFailed;
}
