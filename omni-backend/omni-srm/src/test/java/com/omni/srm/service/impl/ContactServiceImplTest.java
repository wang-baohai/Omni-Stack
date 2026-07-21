package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.entity.SrmSupplierContact;
import com.omni.srm.mapper.SrmSupplierContactMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.security.SrmTenantContext;
import com.omni.srm.service.support.SrmRecordAccessGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 联系人父路径校验和主要联系人约束测试。 */
@ExtendWith(MockitoExtension.class)
class ContactServiceImplTest {

    @Mock private SrmSupplierContactMapper contactMapper;
    @Mock private SrmSupplierMapper supplierMapper;
    @Mock private SrmRecordAccessGuard accessGuard;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "srm-contact-test");
        assistant.setCurrentNamespace("com.omni.srm.mapper.SrmSupplierContactMapper");
        TableInfoHelper.initTableInfo(assistant, SrmSupplierContact.class);
    }

    /** 清理租户上下文。 */
    @AfterEach
    void clearContext() {
        SrmTenantContext.clear();
    }

    /** 子资源必须属于 URL 指定的供应商。 */
    @Test
    void shouldRejectMismatchedSupplierPath() {
        SrmSupplierContact contact = new SrmSupplierContact();
        contact.setId(2L);
        contact.setSupplierId(10L);
        when(accessGuard.requireContact(2L)).thenReturn(contact);
        ContactServiceImpl service = new ContactServiceImpl(contactMapper, supplierMapper, accessGuard);

        assertThatThrownBy(() -> service.get(11L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(404);
    }

    /** 新建主要联系人前必须清除同供应商旧的主要标记。 */
    @Test
    void shouldClearPreviousPrimaryBeforeCreate() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        SrmSupplier supplier = new SrmSupplier();
        supplier.setId(10L);
        when(supplierMapper.selectVisibleForUpdate(10L)).thenReturn(supplier);
        SrmRequests.CreateContactRequest request = new SrmRequests.CreateContactRequest();
        request.setName("张三");
        request.setPrimaryFlag(true);
        ContactServiceImpl service = new ContactServiceImpl(contactMapper, supplierMapper, accessGuard);

        service.create(10L, request);

        verify(contactMapper).update(ArgumentMatchers.isNull(), ArgumentMatchers.any());
        verify(contactMapper).insert(ArgumentMatchers.<SrmSupplierContact>argThat(
                contact -> Boolean.TRUE.equals(contact.getPrimaryFlag())));
    }

    /** 乐观锁版本冲突必须返回 409 并回滚命令。 */
    @Test
    void shouldRejectStaleContactVersion() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        SrmSupplierContact contact = new SrmSupplierContact();
        contact.setId(2L);
        contact.setSupplierId(10L);
        when(accessGuard.requireContact(2L)).thenReturn(contact);
        when(supplierMapper.selectVisibleForUpdate(10L)).thenReturn(new SrmSupplier());
        SrmRequests.UpdateContactRequest request = new SrmRequests.UpdateContactRequest();
        request.setVersion(0);
        request.setName("李四");
        ContactServiceImpl service = new ContactServiceImpl(contactMapper, supplierMapper, accessGuard);

        assertThatThrownBy(() -> service.update(10L, 2L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }
}
