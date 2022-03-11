# TODO

- Scope management is not perfect: if a Resource is declared in a nested scope, it cannot refer to a top level resource
  but will be initialized as new. Maybe a solution could be to use a marker to refer to top-level resources in nested
  scopes.
- Nested levels work only on statements, but in FDL not only statements are nested. We need to find a clean way to
  understand if the resource is initialized in a nested scope. Here the path-based scope resolution shows its limits.