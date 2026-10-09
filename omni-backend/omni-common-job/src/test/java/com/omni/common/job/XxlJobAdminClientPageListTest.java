package com.omni.common.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * XXL-JOB 3.3.1 分页协议回归测试（AD-01 / R13b readinessState 根因）。
 * <p>旧实现用 {@code start/length} 请求参数与 {@code recordsTotal} 响应字段，均与 Admin 实际契约不符：
 * Admin 会忽略不认识的参数固定返回同一页，而 {@code recordsTotal} 取不到恒为 {@code -1}，
 * 导致 {@link XxlJobAdminClient#pageList(int, String)} 的 while 循环永不退出——该方法被
 * {@code ApplicationRunner} 同步调用，不返回即阻断 {@code ApplicationReadyEvent}，
 * 使容器 readiness 永久 {@code OUT_OF_SERVICE}。</p>
 *
 * @author Omni-Stack Team
 */
class XxlJobAdminClientPageListTest {

    private static final String ADMIN = "http://localhost:19999/xxl-job-admin";
    private static final String PAGE_LIST_URL = ADMIN + "/jobinfo/pageList";

    private XxlJobAdminClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        client = new XxlJobAdminClient(ADMIN, "admin", "password");
        // 跳过真实登录（本测试只验证分页协议字段与终止条件，不测认证）
        ReflectionTestUtils.setField(client, "sessionCookie", "xxl_job_login_token=fake");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    }

    @Test
    @DisplayName("pageList 必须正确读取 total 字段并在首页取满后结束（字段名错误会导致多发一次未登记的请求，server.verify()报错）")
    void pageListUsesCorrectPaginationFieldsAndTerminates() {
        server.expect(requestTo(PAGE_LIST_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"code\":200,\"msg\":null,\"data\":{\"offset\":0,\"pagesize\":100,"
                                + "\"data\":[{\"id\":1,\"executorHandler\":\"h1\"}],"
                                + "\"total\":1}}",
                        MediaType.APPLICATION_JSON));

        List<Map<String, Object>> jobs = client.pageList(7, null);

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).get("executorHandler")).isEqualTo("h1");
        // 只应发生一次请求：total=1 已在首页取满，无需再翻页；若字段名仍错误取不到 total 会多请求导致此断言失败。
        server.verify();
    }

    @Test
    @DisplayName("多页取满：total 跨两页时按 offset 递增直到取满，正常结束不重复")
    void pageListPaginatesAcrossPagesUntilTotalReached() {
        server.expect(requestTo(PAGE_LIST_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"code\":200,\"msg\":null,\"data\":{\"offset\":0,\"pagesize\":100,"
                                + "\"data\":[{\"id\":1,\"executorHandler\":\"h1\"}],"
                                + "\"total\":2}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(PAGE_LIST_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"code\":200,\"msg\":null,\"data\":{\"offset\":100,\"pagesize\":100,"
                                + "\"data\":[{\"id\":2,\"executorHandler\":\"h2\"}],"
                                + "\"total\":2}}",
                        MediaType.APPLICATION_JSON));

        List<Map<String, Object>> jobs = client.pageList(7, null);

        assertThat(jobs).hasSize(2);
        assertThat(jobs).extracting(j -> j.get("executorHandler")).containsExactly("h1", "h2");
        server.verify();
    }

    @Test
    @DisplayName("若 Admin 因参数不匹配反复返回同一页（重复页），必须显式抛错终止，不能无限循环")
    void pageListThrowsInsteadOfLoopingForeverWhenNoProgress() {
        // total=50 表示远端实际有50条任务；但每页都固定返回同一条（模拟 Admin 忽略 offset 参数、
        // 因请求参数名不匹配而始终固定返回第一页），第2次响应与第1次内容完全相同则应触发重复页保护。
        String samePage = "{\"code\":200,\"msg\":null,\"data\":{\"offset\":0,\"pagesize\":100,"
                + "\"data\":[{\"id\":1,\"executorHandler\":\"h1\"}],\"total\":50}}";
        server.expect(requestTo(PAGE_LIST_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(samePage, MediaType.APPLICATION_JSON));
        server.expect(requestTo(PAGE_LIST_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(samePage, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.pageList(7, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("重复页");
    }

    @Test
    @DisplayName("getJobGroupId 也必须用 offset/pagesize（不再用 start/length）")
    void getJobGroupIdUsesCorrectPaginationFields() {
        server.expect(requestTo(ADMIN + "/jobgroup/pageList")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"code\":200,\"msg\":null,\"data\":{\"offset\":0,\"pagesize\":100,"
                                + "\"data\":[{\"id\":5,\"appname\":\"omni-auth\"}],\"total\":1}}",
                        MediaType.APPLICATION_JSON));

        int groupId = client.getJobGroupId("omni-auth");

        assertThat(groupId).isEqualTo(5);
        server.verify();
    }
}
