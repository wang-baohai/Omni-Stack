package com.omni.workflow.dto.internal;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量解析内部模型版本请求。
 *
 * @author Omni-Stack Team
 */
@Data
public class InternalModelVersionResolveRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待解析版本 ID，单次最多 200 个。 */
    @NotEmpty(message = "模型版本 ID 不能为空")
    @Size(max = 200, message = "模型版本 ID 单次最多 200 个")
    private List<@NotNull @Positive Long> modelVersionIds;
}
