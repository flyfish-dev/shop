# Flyfish Platform Brand Identity

The Flyfish low-code platform uses the transparent flying-fish illustration as its product identity. This identity is intentionally distinct from the geometric green Flyfish corporate mark used by `flyfish.dev`, File Viewer, and Office Preview.

## Usage

- `web/src/assets/logo.png`: optimized 512px runtime image for the platform home page, navigation, footer, legal page, and large brand placement.
- `web/src/assets/flyfish-dev-logo-transparent.png`: optimized 512px runtime image for authentication surfaces.
- `web/public/favicon.ico`: low-code platform favicon in 16px, 32px, and 48px sizes.
- `flyfish-platform/src/main/resources/static/favicon.ico`: server-side fallback for the same low-code identity.
- Android launcher resources keep their platform-specific adaptive background because launcher icons require a different safe-zone treatment.
- `web/src/assets/shop/flyfish-shop-logo.png` is the Flyfish Shop identity and must not be replaced by either the platform illustration or the corporate mark.

The canonical true-color master and optimized derivatives are maintained in the shared Flyfish workspace brand registry. Runtime images must retain transparency and must not introduce a white fill inside the illustration.
