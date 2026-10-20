package group.flyfish.dev.shop.converter.impl;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.po.ShopItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShopOrderFormParamValueTest {

    @Test
    void acceptsOriginArraysFromTheFrontendJsonPayload() {
        ShopOrderDto dto = JacksonUtils.readValue("""
                {"properties":{"orderForm":{"origins":[
                  "http://customer.example.com", "http://172.16.105.2", "http://198.51.100.25"
                ]}}}
                """, ShopOrderDto.class);

        assertEquals(List.of("http://customer.example.com", "http://172.16.105.2", "http://198.51.100.25"),
                form(true).validateSubmitted(dto).get("origins"));
    }

    @Test
    void normalizesArraysAndTextareaTextToTheSameOrigins() {
        List<String> origins = List.of("HTTPS://Docs.Example.com/path", "http://192.168.1.10:8080");
        Map<String, Object> fromArray = validate(origins, true);

        assertEquals(Map.of("origins", List.of("https://docs.example.com", "http://192.168.1.10:8080")), fromArray);
        assertEquals(fromArray, validate("HTTPS://Docs.Example.com/path\r\nhttp://192.168.1.10:8080", true));
        assertEquals(fromArray, validate(fromArray.get("origins"), true));
    }

    @Test
    void deduplicatesOriginArraysWithoutChangingTheirOrder() {
        assertEquals(List.of("https://docs.example.com", "http://192.168.1.10"),
                validate(List.of("https://docs.example.com/first", "http://192.168.1.10",
                        "https://docs.example.com/second"), true).get("origins"));
    }

    @Test
    void rejectsEmptyRequiredOriginArrays() {
        BusinessException error = assertThrows(BusinessException.class, () -> validate(List.of(), true));
        assertEquals("ORDER_FORM_REQUIRED", error.getCode());
    }

    @Test
    void omitsEmptyOptionalOriginArrays() {
        assertEquals(Map.of(), validate(List.of(), false));
    }

    @Test
    void enforcesTheOriginLimitForArrays() {
        BusinessException error = assertThrows(BusinessException.class, () -> validate(List.of(
                "https://a.example.com", "https://b.example.com", "https://c.example.com",
                "https://d.example.com", "https://e.example.com", "https://f.example.com"), true));
        assertEquals("ORDER_FORM_TOO_MANY_ORIGINS", error.getCode());
    }

    @Test
    void rejectsAnInvalidAddressWithinAnOriginArray() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> validate(List.of("https://docs.example.com", "ftp://files.example.com"), true));
        assertEquals("ORDER_FORM_URL_INVALID", error.getCode());
    }

    @Test
    void enforcesTheLengthLimitForArrays() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> validate(List.of("https://docs.example.com/" + "a".repeat(2048)), true));
        assertEquals("ORDER_FORM_TOO_LONG", error.getCode());
    }

    private Map<String, Object> validate(Object origins, boolean required) {
        ShopOrderDto dto = new ShopOrderDto();
        dto.setProperties(Map.of("orderForm", Map.of("origins", origins)));
        return form(required).validateSubmitted(dto);
    }

    private ShopOrderFormParamValue form(boolean required) {
        ShopItem item = new ShopItem();
        item.setParams("""
                {"orderForm":{"enabled":true,"fields":[{
                  "key":"origins","label":"授权域名 / IP","type":"textarea",
                  "required":%s,"target":"license.allowedOrigins","normalize":"origin",
                  "maxLength":2048,"maxItems":5
                }]}}
                """.formatted(required));
        return ShopOrderFormParamValue.fromItem(item);
    }
}
