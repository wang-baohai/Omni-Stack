package com.omni.asset.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 资产分页查询公共参数。
 *
 * @author Omni-Stack Team
 */
@Data
public class AssetPageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 页码，从 1 开始。 */
    @Min(value = 1, message = "页码不能小于 1")
    private int page = 1;

    /** 每页数量，最大 100。 */
    @Min(value = 1, message = "每页数量不能小于 1")
    @Max(value = 100, message = "每页数量不能超过 100")
    private int size = 10;
}
