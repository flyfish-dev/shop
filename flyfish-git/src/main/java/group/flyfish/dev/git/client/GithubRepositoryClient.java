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
 * GitHub repository collaborator API.
 */
@HttpExchange
public interface GithubRepositoryClient {

    @GetExchange("/user/repos")
    Mono<List<GitRepositoryApiRepo>> listRepositories(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                      @RequestParam String visibility,
                                                      @RequestParam String affiliation,
                                                      @RequestParam String sort,
                                                      @RequestParam String direction,
                                                      @RequestParam("per_page") Integer perPage,
                                                      @RequestParam Integer page);

    @GetExchange("/repos/{owner}/{repo}/collaborators")
    Mono<List<GitRepositoryCollaborator>> listCollaborators(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                            @PathVariable String owner,
                                                            @PathVariable String repo,
                                                            @RequestParam String affiliation,
                                                            @RequestParam("per_page") Integer perPage,
                                                            @RequestParam Integer page);

    @GetExchange("/repos/{owner}/{repo}/collaborators/{username}/permission")
    Mono<GitRepositoryCollaboratorPermission> getCollaboratorPermission(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String username);

    @PutExchange("/repos/{owner}/{repo}/collaborators/{username}")
    Mono<ResponseEntity<String>> addCollaborator(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                 @PathVariable String owner,
                                                 @PathVariable String repo,
                                                 @PathVariable String username,
                                                 @RequestBody GitCollaboratorRequest request);
}
