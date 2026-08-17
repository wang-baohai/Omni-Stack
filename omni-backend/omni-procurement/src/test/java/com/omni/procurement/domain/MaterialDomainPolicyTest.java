package com.omni.procurement.domain;

import com.omni.common.core.result.BusinessException;
import com.omni.procurement.entity.ProcMaterial;
import com.omni.procurement.entity.ProcMaterialCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 物料目录不变量测试。 */
class MaterialDomainPolicyTest {

    /** 资产管理只允许离散计数单位。 */
    @Test
    void shouldOnlyAllowDiscreteUnitsForAssetManagedMaterial() {
        MaterialDomainPolicy.validateAssetManaged(true, "ea");
        MaterialDomainPolicy.validateAssetManaged(true, "PCS");
        MaterialDomainPolicy.validateAssetManaged(true, "unit");
        MaterialDomainPolicy.validateAssetManaged(true, "SET");

        assertThatThrownBy(() -> MaterialDomainPolicy.validateAssetManaged(true, "KG"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);
        MaterialDomainPolicy.validateAssetManaged(false, "KG");
    }

    /** 物料品类支持任意层级嵌套。 */
    @Test
    void shouldAllowArbitraryCategoryDepth() {
        // 第二级：父品类启用，应该通过
        ProcMaterialCategory secondLevel = new ProcMaterialCategory();
        secondLevel.setId(20L);
        secondLevel.setParentId(10L);
        secondLevel.setStatus(1);
        MaterialDomainPolicy.validateCategoryLevel(20L, secondLevel);

        // 第三级：父品类（第二级）启用，应该通过
        ProcMaterialCategory thirdLevel = new ProcMaterialCategory();
        thirdLevel.setId(30L);
        thirdLevel.setParentId(20L);
        thirdLevel.setStatus(1);
        MaterialDomainPolicy.validateCategoryLevel(30L, thirdLevel);

        // 停用的父品类应该拒绝
        ProcMaterialCategory stoppedParent = new ProcMaterialCategory();
        stoppedParent.setId(40L);
        stoppedParent.setParentId(10L);
        stoppedParent.setStatus(0);
        assertThatThrownBy(() -> MaterialDomainPolicy.validateCategoryLevel(40L, stoppedParent))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }

    /** 物料只能关联叶子品类。 */
    @Test
    void shouldRequireLeafCategoryForMaterial() {
        MaterialDomainPolicy.requireLeafCategory(false);
        assertThatThrownBy(() -> MaterialDomainPolicy.requireLeafCategory(true))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);
    }

    /** 只有活动物料才可加入请购。 */
    @Test
    void shouldRequireActiveMaterialForRequisition() {
        ProcMaterial material = new ProcMaterial();
        material.setStatus(MaterialDomainPolicy.INACTIVE);

        assertThatThrownBy(() -> MaterialDomainPolicy.requireActiveMaterial(material))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }
}
