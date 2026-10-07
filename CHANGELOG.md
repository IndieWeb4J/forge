# Changelog

## [0.2.4](https://github.com/Marchland/forge/compare/v0.2.3...v0.2.4) (2026-10-07)


### Bug Fixes

* **micropub:** reject unknown content types, normalize form arrays, absolute media endpoint ([dd417f8](https://github.com/Marchland/forge/commit/dd417f82057d1246f70a39675557bb74d0198a84))
* **micropub:** reject unknown content types, normalize form arrays, absolute media endpoint ([92baac7](https://github.com/Marchland/forge/commit/92baac7517404f533818fa893270336aded80393))

## [0.2.3](https://github.com/Marchland/forge/compare/v0.2.2...v0.2.3) (2026-10-06)


### Bug Fixes

* **micropub:** conform error responses and enforce token authorization ([49c9d9a](https://github.com/Marchland/forge/commit/49c9d9a055d0b5850ec813ebac455428d55f8e9f))
* **micropub:** conform error responses and enforce token authorization ([c863bd7](https://github.com/Marchland/forge/commit/c863bd7cfa5f21b36c5cf9a153c8ce4f4bb1d98f))

## [0.2.2](https://github.com/Marchland/forge/compare/v0.2.1...v0.2.2) (2026-10-06)


### Miscellaneous Chores

* move packages to Marchland and rename mf24j -&gt; microformats2 ([415ed6b](https://github.com/Marchland/forge/commit/415ed6b2fc6d3d20dd7b9267d1fdd6254eb45600))
* move packages to Marchland and rename mf24j -&gt; microformats2 ([32695be](https://github.com/Marchland/forge/commit/32695bec73df5aab20161dba7c21d4c1cb6241b7))


### Build System

* bump content-client to 2.0.2 ([44d2a50](https://github.com/Marchland/forge/commit/44d2a5081bc83dfdea60db71ea1b1a6837a33c0e))
* bump microformats2 to 0.1.2 ([88c8f66](https://github.com/Marchland/forge/commit/88c8f66e5c5922ec52a859a7a469cada9d370f95))

## [0.2.1](https://github.com/jacobsandersen/forge/compare/v0.2.0...v0.2.1) (2026-10-06)


### Tests

* cover Micropub q= queries ([5c068e2](https://github.com/jacobsandersen/forge/commit/5c068e2adf87afb8542237e5388a90c6baa3cedb))
* cover the Micropub q= queries over the content read API ([d91989c](https://github.com/jacobsandersen/forge/commit/d91989c94f974829e56011e7fcf35f41af461636))

## [0.2.0](https://github.com/jacobsandersen/forge/compare/v0.1.0...v0.2.0) (2026-10-06)


### Features

* add Micropub facade wired to content-client contract ([34904df](https://github.com/jacobsandersen/forge/commit/34904df6611bac09b9c57c5067e54c44776cc868))
* implement the Micropub facade against content-client ([1815783](https://github.com/jacobsandersen/forge/commit/1815783f8e17a435f1ccca52cc0ddee1818fe1bd))


### Build System

* consume content-client from Bastion packages ([8476bce](https://github.com/jacobsandersen/forge/commit/8476bcee9d92ac52ce46d6ce86d078cf636a5902))


### Continuous Integration

* add lint and test workflow ([658c74b](https://github.com/jacobsandersen/forge/commit/658c74b96a70f723813a288bfb95a84cffa798c4))
* add release-please and a distroless image pipeline ([8c93d62](https://github.com/jacobsandersen/forge/commit/8c93d621124b7c6566e75b6fc834b248a769f461))
* authenticate package reads with a dedicated PACKAGES_TOKEN ([9f3a73d](https://github.com/jacobsandersen/forge/commit/9f3a73d0631d6d5bea3456b68767c7a493be0525))
* read private content-client via a packages token; add microformats2/sigil repos, sigil-client 0.2.0 ([ec852c3](https://github.com/jacobsandersen/forge/commit/ec852c376dd98cdd8dd6aee864a78328be1b6c5d))
* release-please + distroless image pipeline ([dd2386c](https://github.com/jacobsandersen/forge/commit/dd2386cba481a8921856e3b16277d3e0e9cb08d1))
