package group.flyfish.dev.shop.xianyu;

import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.bridge.*;
import group.flyfish.dev.common.upload.repository.FileMetadataRepository;
import group.flyfish.dev.shop.repository.ShopContractFileRepository;
import group.flyfish.dev.shop.repository.ShopContractRepository;
import group.flyfish.dev.shop.service.ShopOrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class XianyuFileBridgeController {
    private final XianyuBridgeGuard guard;
    private final ShopOrderService orders;
    private final AuthUserGateway users;
    private final FileMetadataRepository files;
    private final ShopContractFileRepository contracts;
    private final ShopContractRepository contractDefinitions;
    private final JsonMapper json;
    private final WebClient self;
    public XianyuFileBridgeController(XianyuBridgeGuard guard,ShopOrderService orders,AuthUserGateway users,
                                     FileMetadataRepository files,ShopContractFileRepository contracts,ShopContractRepository contractDefinitions,
                                     JsonMapper json,WebClient.Builder builder,@Value("${server.port:10082}") int port) {
        this.guard=guard;this.orders=orders;this.users=users;this.files=files;this.contracts=contracts;this.contractDefinitions=contractDefinitions;this.json=json;
        self=builder.clone().baseUrl("http://127.0.0.1:"+port).build();
    }
    @PostMapping("/internal/xianyu/shop/file")
    public Mono<Object> file(@RequestBody BridgeRequest request) {
        return guard.accept(request,"/internal/xianyu/shop/file").flatMap(body -> {
            JsonNode input=json.readTree(body);String kind=input.path("kind").asText();
            if("CONTRACT".equals(kind)) return contracts.findById(input.path("fileId").asLong())
                    .filter(f -> Boolean.TRUE.equals(f.getEnabled())).flatMap(f -> contractDefinitions.findById(f.getContractId())
                            .filter(c -> Boolean.TRUE.equals(c.getEnabled())).flatMap(c -> files.findByHash(f.getFileHash())))
                    .flatMap(metadata -> readFile(metadata.getPath(),metadata.getOriginalFilename(),metadata.getContentType())).cast(Object.class);
            if("PUBLIC_FILE".equals(kind)) {
                String path=input.path("path").asText();
                if(!path.matches("/images/[0-9]{4}/[0-9]{2}/[a-f0-9]{64}/[a-f0-9]{64}(?:\\.[a-z0-9]{1,16})?"))
                    return Mono.error(new IllegalArgumentException("附件路径不正确"));
                String hash=path.split("/")[4];
                return files.findByHash(hash).filter(f -> path.equals(f.getUrl()))
                        .flatMap(f -> readFile(f.getPath(),f.getOriginalFilename(),f.getContentType())).cast(Object.class);
            }
            return users.authenticate(List.of(input.path("token").asText())).switchIfEmpty(Mono.error(new IllegalArgumentException("请先登录")))
                    .flatMap(user -> {
                        if("DELIVERY".equals(kind)) return orders.downloadDeliveryFile(user,input.path("orderNo").asText(),input.path("fileCode").asText(),input.path("licenseNo").asText(null))
                                .map(f -> Map.of("name",f.getName(),"contentType",f.getContentType(),"content",f.getContent())).cast(Object.class);
                        if("UPLOAD".equals(kind)) {
                            byte[] bytes=java.util.Base64.getDecoder().decode(input.path("content").asText());
                            if(bytes.length>5*1024*1024) return Mono.error(new IllegalArgumentException("附件超过5MB"));
                            var multipart=new org.springframework.http.client.MultipartBodyBuilder();
                            multipart.part("file",new org.springframework.core.io.ByteArrayResource(bytes)).filename(input.path("name").asText("attachment"));
                            return self.post().uri("/portal/files").headers(h -> h.setBearerAuth(input.path("token").asText())).bodyValue(multipart.build())
                                    .retrieve().bodyToMono(JsonNode.class).cast(Object.class);
                        }
                        return Mono.error(new IllegalArgumentException("文件操作不支持"));
                    });
        }).switchIfEmpty(Mono.error(new IllegalArgumentException("文件不存在")));
    }
    private Mono<Map<String,Object>> readFile(String storedPath,String name,String type) {
        return Mono.fromCallable(() -> {
            Path root=Path.of("data/images").toRealPath(),file=Path.of(storedPath).toRealPath();
            if(!file.startsWith(root) || Files.size(file)>10*1024*1024) throw new IllegalArgumentException("文件不可读取");
            return Map.<String,Object>of("name",name,"contentType",type,"content",Files.readAllBytes(file));
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }
}
