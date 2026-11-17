package com.hiyori.mall;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 走完整 Spring 容器 + H2 + Flyway 迁移，按小程序真实调用顺序测一遍 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.upload.dir=${java.io.tmpdir}/hiyori-test-uploads")
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Test
    void homeReturnsSeededSectionsAndCategories() throws Exception {
        mvc.perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.categories.length()", is(10)))
                .andExpect(jsonPath("$.data.categories[0].id", is("deals")))
                .andExpect(jsonPath("$.data.categories[0].nameEn", is("On Sale")))
                .andExpect(jsonPath("$.data.categories[0].count", greaterThan(100)))
                .andExpect(jsonPath("$.data.banners[0].titleEn", not(is(""))))
                .andExpect(jsonPath("$.data.sections[0].id", is("deals")))
                .andExpect(jsonPath("$.data.sections[0].titleEn", is("This Week's Deals")))
                .andExpect(jsonPath("$.data.sections[0].list.length()", is(8)))
                .andExpect(jsonPath("$.data.sections[0].list[0].image", startsWith("/files/products/")))
                .andExpect(jsonPath("$.data.sections[0].list[0].thumb", startsWith("/files/products/thumb/")))
                .andExpect(jsonPath("$.data.sections[0].list[0].nameEn", not(is(""))))
                .andExpect(jsonPath("$.data.hotKeywords[*].keyword", hasItem("海底捞")))
                .andExpect(jsonPath("$.data.hotKeywords[*].keywordEn", hasItem("Haidilao")));
    }

    @Test
    void listByCategoryIsPaged() throws Exception {
        mvc.perform(get("/api/products").param("cat", "frozen").param("size", "20"))
                .andExpect(jsonPath("$.data.total", greaterThan(20)))
                .andExpect(jsonPath("$.data.list.length()", is(20)))
                .andExpect(jsonPath("$.data.list[*].cat", everyItem(is("frozen"))));

        mvc.perform(get("/api/products").param("cat", "frozen").param("size", "20").param("page", "2"))
                .andExpect(jsonPath("$.data.page", is(2)))
                .andExpect(jsonPath("$.data.list.length()", greaterThan(0)));

        mvc.perform(get("/api/products").param("cat", "frozen").param("size", "999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dealsIsAVirtualCategoryOfDiscountedProducts() throws Exception {
        // 加多宝：2件3€（只有促销，没有特价）；黄飞红芥末花生：手写特价 1.80
        mvc.perform(get("/api/products").param("cat", "deals").param("size", "60"))
                .andExpect(jsonPath("$.data.total", greaterThan(100)))
                .andExpect(jsonPath("$.data.list[*].id", hasItem("p112")))
                .andExpect(jsonPath("$.data.list[*].id", hasItem("p140")))
                .andExpect(jsonPath("$.data.list[*].id", not(hasItem("p114"))));
    }

    @Test
    void searchMatchesChineseEnglishBarcodeAndCategory() throws Exception {
        // 命中中文名
        mvc.perform(get("/api/products/search").param("keyword", "海底捞"))
                .andExpect(jsonPath("$.data.total", greaterThan(0)));
        // 命中英文名，不分大小写
        mvc.perform(get("/api/products/search").param("keyword", "shin ramyun"))
                .andExpect(jsonPath("$.data.total", greaterThan(3)))
                .andExpect(jsonPath("$.data.list[*].id", hasItem("p820")));
        // 条码
        mvc.perform(get("/api/products/search").param("keyword", "8801043150620"))
                .andExpect(jsonPath("$.data.total", is(1)))
                .andExpect(jsonPath("$.data.list[0].id", is("p820")));
        // 命中分类名「酱料调味」/ "Meat & Seafood" → 返回整个分类
        mvc.perform(get("/api/products/search").param("keyword", "酱料"))
                .andExpect(jsonPath("$.data.total", greaterThan(100)));
        mvc.perform(get("/api/products/search").param("keyword", "seafood"))
                .andExpect(jsonPath("$.data.total", greaterThan(10)));
    }

    @Test
    void detailReturnsBilingualInfoAndRelatedProducts() throws Exception {
        mvc.perform(get("/api/products/p140"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("黄飞红 芥末花生 110g")))
                .andExpect(jsonPath("$.data.nameEn", is("Huangfeihong Wasabi Peanuts 110g")))
                .andExpect(jsonPath("$.data.price", is(2.50)))
                .andExpect(jsonPath("$.data.salePrice", is(1.80)))
                .andExpect(jsonPath("$.data.catName", is("零食饼干")))
                .andExpect(jsonPath("$.data.catNameEn", is("Snacks & Biscuits")))
                .andExpect(jsonPath("$.data.barcode", is("6925843404423")))
                .andExpect(jsonPath("$.data.related.length()", is(6)))
                .andExpect(jsonPath("$.data.related[*].cat", everyItem(is("snack"))))
                .andExpect(jsonPath("$.data.related[*].id", not(hasItem("p140"))));

        // 整箱价之类的补充说明
        mvc.perform(get("/api/products/p112"))
                .andExpect(jsonPath("$.data.promo", is("2件3€")))
                .andExpect(jsonPath("$.data.promoEn", is("2 for €3")))
                .andExpect(jsonPath("$.data.remarkEn", containsString("Full case of 24")));
    }

    @Test
    void unknownProductIs404() throws Exception {
        mvc.perform(get("/api/products/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(40400)));
    }

    @Test
    void adminEndpointsRequireLoginAndAdminRole() throws Exception {
        mvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());

        String userToken = login("someone");
        mvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/products").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.data.role", is("USER")));
    }

    @Test
    void adminCanCreateUpdateOffShelfAndDeleteProduct() throws Exception {
        String admin = "Bearer " + login("dev-admin");
        // 先访问一次首页，确认缓存会在后面被正确清掉
        mvc.perform(get("/api/home")).andExpect(status().isOk());

        String body = """
                {"cat":"snack","name":"测试辣条","nameEn":"Test Latiao","spec":"100g / 袋","price":2.5,"badge":"新品"}
                """;
        String res = mvc.perform(post("/api/admin/products").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is(1)))
                .andReturn().getResponse().getContentAsString();
        String id = om.readTree(res).at("/data/id").asText();

        // 新商品排在分类最前面（sort 默认 0）
        mvc.perform(get("/api/products").param("cat", "snack"))
                .andExpect(jsonPath("$.data.list[*].name", hasItem("测试辣条")));

        mvc.perform(put("/api/admin/products/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("2.5", "3")))
                .andExpect(jsonPath("$.data.price", is(3)));

        mvc.perform(patch("/api/admin/products/" + id + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());

        mvc.perform(delete("/api/admin/products/" + id).header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/products").header("Authorization", admin).param("keyword", "测试辣条"))
                .andExpect(jsonPath("$.data.total", is(0)));
    }

    @Test
    void createRejectsInvalidInput() throws Exception {
        String admin = "Bearer " + login("dev-admin");
        mvc.perform(post("/api/admin/products").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cat\":\"snack\",\"name\":\"\",\"price\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(40000)));
        mvc.perform(post("/api/admin/products").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cat\":\"no-such-cat\",\"name\":\"x\",\"price\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", containsString("分类不存在")));
        // 「特价」是自动汇总的虚拟分类，不能直接往里放商品
        mvc.perform(post("/api/admin/products").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cat\":\"deals\",\"name\":\"x\",\"price\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadAcceptsRealImagesOnly() throws Exception {
        String admin = "Bearer " + login("dev-admin");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        String res = mvc.perform(multipart("/api/admin/upload")
                        .file(new MockMultipartFile("file", "../../evil.png", "image/png", png))
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url", not(containsString("evil"))))
                .andReturn().getResponse().getContentAsString();

        String path = om.readTree(res).at("/data/url").asText().replace("http://localhost:8080", "");
        mvc.perform(get(path)).andExpect(status().isOk());

        mvc.perform(multipart("/api/admin/upload")
                        .file(new MockMultipartFile("file", "a.png", "image/png", "<script>".getBytes()))
                        .header("Authorization", admin))
                .andExpect(status().isBadRequest());
    }

    private String login(String code) throws Exception {
        String res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = om.readTree(res);
        return json.at("/data/token").asText();
    }
}
