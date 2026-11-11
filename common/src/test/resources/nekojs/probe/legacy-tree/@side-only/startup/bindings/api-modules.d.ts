// API modules for the selected script environment.
declare module "nekojs/api/stable" {
}
declare function require(id: "nekojs/api/stable"): typeof import("nekojs/api/stable");
declare module "nekojs/api/version" {
}
declare function require(id: "nekojs/api/version"): typeof import("nekojs/api/version");
declare function require(id: string): unknown;
