package com.omni.crm.integration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.crm.config.MybatisPlusConfig;
import com.omni.crm.entity.CrmLead;
import com.omni.crm.security.CrmDataScopeContext;
import com.omni.crm.security.CrmTenantContext;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRM 真实 MySQL 租户、数据权限与分页拦截器集成测试。
 * <p>设置 {@code CRM_TEST_MYSQL_URL} 后启用，测试库必须允许创建和删除
 * {@code crm_lead} 测试表。</p>
 */
@EnabledIfEnvironmentVariable(named = "CRM_TEST_MYSQL_URL", matches = ".+")
class CrmMysqlInterceptorIntegrationTest {

    private static PooledDataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    /** 创建真实 MySQL 数据源、测试表和 MyBatis-Plus 拦截器链。 */
    @BeforeAll
    static void initialize() throws Exception {
        String url = System.getenv("CRM_TEST_MYSQL_URL");
        String username = environmentOrDefault("CRM_TEST_MYSQL_USERNAME", "root");
        String password = requireEnvironment("CRM_TEST_MYSQL_PASSWORD");
        dataSource = new PooledDataSource("com.mysql.cj.jdbc.Driver", url, username, password);
        execute("DROP TABLE IF EXISTS crm_lead");
        execute("""
                CREATE TABLE crm_lead (
                    id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    owner_user_id BIGINT NOT NULL,
                    owner_unit_id BIGINT NOT NULL,
                    deleted TINYINT NOT NULL DEFAULT 0
                ) ENGINE=InnoDB
                """);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ScopedLeadMapper.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();
        factoryBean.setPlugins(interceptor);
        sqlSessionFactory = factoryBean.getObject();
    }

    /** 删除测试表并释放连接池。 */
    @AfterAll
    static void destroy() throws Exception {
        if (dataSource != null) {
            execute("DROP TABLE IF EXISTS crm_lead");
            dataSource.forceCloseAll();
        }
    }

    /** 为每个用例准备两个租户和不同 owner 的记录。 */
    @BeforeEach
    void seed() throws Exception {
        execute("DELETE FROM crm_lead");
        execute("""
                INSERT INTO crm_lead (id, tenant_id, owner_user_id, owner_unit_id, deleted) VALUES
                    (1, 1, 10, 100, 0),
                    (2, 1, 11, 101, 0),
                    (3, 2, 10, 100, 0)
                """);
        CrmTenantContext.set(new CrmTenantContext.RequestIdentity(10L, 1L, "integration"));
    }

    /** 每个用例后必须清理线程上下文。 */
    @AfterEach
    void clearContexts() {
        CrmDataScopeContext.clear();
        CrmTenantContext.clear();
    }

    /** 分页 records 与 total 必须使用相同 SELF 范围，并排除其他租户。 */
    @Test
    void shouldApplyTenantAndSelfScopeToPageRecordsAndTotal() {
        CrmDataScopeContext.set(scope("SELF", Set.of()));

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            IPage<CrmLead> result = session.getMapper(ScopedLeadMapper.class)
                    .selectScopedPage(new Page<>(1, 10));

            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getRecords()).extracting(CrmLead::getId).containsExactly(1L);
        }
    }

    /** TENANT 范围必须看见当前租户全部记录，但不能看见第二租户。 */
    @Test
    void shouldKeepAllScopeInsideCurrentTenant() {
        CrmDataScopeContext.set(scope("ALL", Set.of()));

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            ScopedLeadMapper mapper = session.getMapper(ScopedLeadMapper.class);

            assertThat(mapper.countScoped()).isEqualTo(2);
            assertThat(mapper.selectScopedById(3L)).isNull();
        }
    }

    /** 缺少数据范围必须失败关闭，COUNT 不能返回业务记录。 */
    @Test
    void shouldFailClosedWithoutDataScope() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            assertThat(session.getMapper(ScopedLeadMapper.class).countScoped()).isZero();
        }
    }

    /** 跨租户 UPDATE 必须被 TenantLine 拦截，同租户 SELF 越权也必须被拒绝。 */
    @Test
    void shouldRejectCrossTenantAndOutOfScopeUpdates() {
        CrmDataScopeContext.set(scope("SELF", Set.of()));

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            ScopedLeadMapper mapper = session.getMapper(ScopedLeadMapper.class);

            assertThat(mapper.updateOwner(3L, 99L)).isZero();
            assertThat(mapper.updateOwner(2L, 99L)).isZero();
            assertThat(mapper.updateOwner(1L, 99L)).isEqualTo(1);
        }
    }

    private static InternalDataScopeDTO scope(String effectiveScope, Set<Long> units) {
        InternalDataScopeDTO scope = new InternalDataScopeDTO();
        scope.setUserId(10L);
        scope.setTenantId(1L);
        scope.setPermissionCode("crm:lead:list");
        scope.setPrimaryUnitId(100L);
        scope.setEffectiveScope(effectiveScope);
        scope.setAccessibleUnitIds(units);
        return scope;
    }

    private static void execute(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("启用 CRM MySQL 集成测试时必须配置环境变量：" + name);
        }
        return value;
    }

    /** 只用于验证真实 SQL 拦截链的测试 Mapper。 */
    interface ScopedLeadMapper {

        /** 分页查询授权线索。 */
        @Select("SELECT id, tenant_id, owner_user_id, owner_unit_id, deleted FROM crm_lead ORDER BY id")
        IPage<CrmLead> selectScopedPage(Page<CrmLead> page);

        /** 统计授权线索。 */
        @Select("SELECT COUNT(*) FROM crm_lead")
        long countScoped();

        /** 按 ID 查询授权线索。 */
        @Select("SELECT id, tenant_id, owner_user_id, owner_unit_id, deleted FROM crm_lead WHERE id = #{id}")
        CrmLead selectScopedById(@Param("id") Long id);

        /** 修改授权线索负责人。 */
        @Update("UPDATE crm_lead SET owner_user_id = #{ownerUserId} WHERE id = #{id}")
        int updateOwner(@Param("id") Long id, @Param("ownerUserId") Long ownerUserId);
    }
}
