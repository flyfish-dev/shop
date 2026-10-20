package group.flyfish.dev.xianyu.web;

import group.flyfish.dev.xianyu.bridge.ApprovedBridgeClient;
import group.flyfish.dev.xianyu.security.XianyuIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class XianyuFileController {
    private final ApprovedBridgeClient bridge;
    private final XianyuIdentityService identities;
    @GetMapping("/xianyu/contracts/{id}")
    public Mono<ResponseEntity<byte[]>> contract(@PathVariable Long id) {
        return bridge.shop("file",Map.of("kind","CONTRACT","fileId",id)).map(this::response);
    }
    @GetMapping("/images/{year}/{month}/{hash}/{name}")
    public Mono<ResponseEntity<byte[]>> attachment(org.springframework.web.server.ServerWebExchange exchange) {
        return bridge.shop("file",Map.of("kind","PUBLIC_FILE","path",exchange.getRequest().getPath().value())).map(this::response);
    }
    @GetMapping("/shops/orders/{no}/delivery/files/{code}")
    public Mono<ResponseEntity<byte[]>> download(@PathVariable String no,@PathVariable String code,
                                                 @RequestParam(required=false) String licenseNo,@RequestHeader("Authorization") String authorization) {
        return identities.authenticate(authorization).flatMap(i -> bridge.shop("file",Map.of("kind","DELIVERY",
                "orderNo",no,"fileCode",code,"licenseNo",licenseNo==null ? "" : licenseNo,"token",identities.portalToken(i)))).map(this::response);
    }
    @PostMapping(value="/portal/files",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<JsonNode> upload(@RequestPart("file") FilePart file,@RequestHeader("Authorization") String authorization) {
        return identities.authenticate(authorization).flatMap(i -> DataBufferUtils.join(file.content(),5*1024*1024).flatMap(buffer -> {
            byte[] bytes=new byte[buffer.readableByteCount()];buffer.read(bytes);DataBufferUtils.release(buffer);
            return bridge.shop("file",Map.of("kind","UPLOAD","name",file.filename(),"content",java.util.Base64.getEncoder().encodeToString(bytes),"token",identities.portalToken(i)));
        }));
    }
    private ResponseEntity<byte[]> response(JsonNode file) {
        byte[] content=java.util.Base64.getDecoder().decode(file.path("content").asText());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.path("contentType").asText("application/octet-stream")))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.path("name").asText(),java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CACHE_CONTROL,"no-store").body(content);
    }
}
