package group.flyfish.dev.git.client;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Gitee API v5 repository client.
 */
@HttpExchange
public interface GiteeRepositoryClient {

    @GetExchange("/user/repos")
    Mono<List<GitRepositoryApiRepo>> listRepositories(@RequestParam("access_token") String accessToken,
                                                      @RequestParam(required = false) String type,
                                                      @RequestParam(required = false) String sort,
                                                      @RequestParam(required = false) String direction,
                                                      @RequestParam("per_page") Integer perPage,
                                                      @RequestParam Integer page);

    @GetExchange("/repos/{owner}/{repo}/collaborators")
    Mono<List<GitRepositoryCollaborator>> listCollaborators(@PathVariable String owner,
                                                            @PathVariable String repo,
                                                            @RequestParam("access_token") String accessToken,
                                                            @RequestParam Integer page,
                                                            @RequestParam("per_page") Integer perPage);

    @GetExchange("/repos/{owner}/{repo}/collaborators/{username}/permission")
    Mono<GitRepositoryCollaboratorPermission> getCollaboratorPermission(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String username,
            @RequestParam("access_token") String accessToken);

    @PutExchange("/repos/{owner}/{repo}/collaborators/{username}")
    Mono<ResponseEntity<String>> addCollaborator(@PathVariable String owner,
                                                 @PathVariable String repo,
                                                 @PathVariable String username,
                                                 @RequestParam("access_token") String accessToken,
                                                 @RequestBody GitCollaboratorRequest request);
}
