package group.flyfish.dev.git.client;

/**
 * Common collaborator payload for Git hosting providers.
 *
 * <p>Gitea and GitHub both accept a {@code permission} field when opening repository
 * collaborator access. Keeping the request typed avoids scattering raw maps through
 * delivery code.</p>
 */
public record GitCollaboratorRequest(String permission) {
}
