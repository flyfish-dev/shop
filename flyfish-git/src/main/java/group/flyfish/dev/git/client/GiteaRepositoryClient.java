package group.flyfish.dev.git.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Gitea repository collaborator API.
 */
@HttpExchange
public interface GiteaRepositoryClient {

    @GetExchange("/api/v1/repos/search")
    Mono<GiteaRepositorySearchResponse> searchRepositories(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                           @RequestParam(required = false) String q,
                                                           @RequestParam("private") Boolean includePrivate,
                                                           @RequestParam String sort,
                                                           @RequestParam String order,
                                                           @RequestParam Integer page,
                                                           @RequestParam Integer limit);

    @GetExchange("/api/v1/repos/{owner}/{repo}/collaborators")
    Mono<List<GitRepositoryCollaborator>> listCollaborators(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                            @PathVariable String owner,
                                                            @PathVariable String repo,
                                                            @RequestParam Integer page,
                                                            @RequestParam Integer limit);

    @GetExchange("/api/v1/repos/{owner}/{repo}/collaborators/{username}/permission")
    Mono<GitRepositoryCollaboratorPermission> getCollaboratorPermission(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String username);

    @PutExchange("/api/v1/repos/{owner}/{repo}/collaborators/{username}")
    Mono<ResponseEntity<Void>> addCollaborator(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                               @PathVariable String owner,
                                               @PathVariable String repo,
                                               @PathVariable String username,
                                               @RequestBody GitCollaboratorRequest request);
}
