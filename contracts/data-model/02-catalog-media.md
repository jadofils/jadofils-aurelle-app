# Phase 2 — Catalog and media (status: frozen 2026-11-14)

Modules: `catalog`, `media`. Conventions and standard columns (`id` UUID v7, `created_at`, `updated_at`, `version`, `archived_at`): [00-conventions.md](00-conventions.md). GraphQL: [../graphql/02-catalog.graphqls](../graphql/02-catalog.graphqls). No personal data in this phase except `media_asset.uploaded_by` (staff id).

Every table carries `organization_id` (scope-ready, D15) unless noted. Money in minor units; currency is the shop currency (`shop_setting.currency`) and stored on `product`.

## category (hierarchical, max depth 3)
| Field | Type | Req | Purpose |
| --- | --- | --- | --- |
| `parent_id` | uuid → category | - | tree; cycle and depth checked in the use case |
| `name` | text 1–80 | Y | "Tops" |
| `slug` | citext, unique per organization, `^[a-z0-9]+(-[a-z0-9]+)*$` | Y | URL |
| `description` | text ≤ 1000 | - | category page intro |
| `image_media_id` | uuid → media_asset | - | tile image |
| `sort_order` | int ≥ 0 default 0 | Y | menu order |
| `active` | boolean default true | Y | hide without deleting |
| `seo_title` / `seo_description` | text ≤ 70 / ≤ 160 | - | crawlable web surface (D12) |

## brand
`name` (Y, 1–80), `slug` (Y, unique per organization), `description` (-), `logo_media_id` (-), `active` (Y). Prototype mock API: Aurelle, Atelier 08, Form & Field.

## product_type (admin-extendable lookup)
`key` (Y, unique per organization: `tee`, `hoodie`, `jacket`, `dress`, `trousers`, `sneaker`, …), `name` (Y), `is_footwear` (Y, shoe wall and shoe group), `illustration_key` (-, prototype flat-art fallback when a product has no photo), `default_size_scale_id` (-).

## size_scale / size_value (admin-extendable lookups)
- `size_scale`: `key` (Y, unique per organization: `TOPS`, `WAIST`, `SHOE_EU`, `KIDS_AGE`, `KIDS_SHOE_EU`), `name` (Y).
- `size_value`: `size_scale_id` (Y), `label` (Y, unique within the scale: "M", "32", "41", "6Y"), `sort_order` (Y), `age_min`/`age_max` (-, 0–18), `height_min_cm`/`height_max_cm` (-, 50–220), `chest_cm`/`waist_cm` (-), `foot_length_mm` (-). Powers the size finder; ranges are checked `min <= max`.

## size_guide
`name` (Y), `size_scale_id` (Y), `body_markdown` (Y), `how_to_measure_media_id` (-). Measurements come from `size_value`.

## product
| Field | Type | Req | Purpose |
| --- | --- | --- | --- |
| `product_number` | bigint, sequence per organization | Y | short human number in SKUs (AUR-00001-…) |
| `slug` | citext, unique per organization | Y | shareable URL |
| `name` | text 1–120 | Y | "Noir Silk Tee" |
| `subtitle` | text ≤ 160 | - | one-line hook on cards |
| `description_markdown` | text ≤ 10 000 | Y | product page |
| `product_type_id` / `category_id` | uuid | Y | |
| `brand_id` | uuid | - | |
| `audience` | text check (`MEN`,`WOMEN`,`BOYS`,`GIRLS`,`KIDS_UNISEX`,`UNISEX`) | Y | audience tabs |
| `status` | text check (`DRAFT`,`ACTIVE`,`ARCHIVED`) default `DRAFT` | Y | lifecycle |
| `hidden` | boolean default false | Y | prototype `hidden`: off the storefront temporarily |
| `badge` | text ≤ 24 | - | "New", "Best seller", "Limited" |
| `currency` | char(3) | Y | shop currency at save time |
| `price_minor` | bigint ≥ 0 | Y | |
| `compare_at_price_minor` | bigint, `> price_minor` when set | - | sale display; price-drop alerts (phase 7) |
| `cost_minor` | bigint ≥ 0 | - | **staff only** (`catalog.product.read_cost`); margin reports |
| `material` / `composition` | text ≤ 200 | - | "silk-cotton", "95% cotton, 5% elastane" |
| `care_instructions` / `fit_notes` | text ≤ 1000 | - | "Cut slightly long" |
| `collection` / `season` | text ≤ 80 | - | "Summer drop" |
| `country_of_origin` | char(2) | - | |
| `weight_grams` | int > 0 | - | shipping estimate (phase 4) |
| `hs_code` / `tax_class` | text | - | customs and tax (D11) |
| `size_scale_id` | uuid | Y | which sizes are possible |
| `size_guide_id` | uuid | - | |
| `launch_at` | timestamptz | - | scheduled drop: invisible to customers before |
| `published_at` | timestamptz | - | first time it became ACTIVE |
| `search_vector` | tsvector, maintained by trigger | Y | name, subtitle, material, tags, colour names (GIN index) |
| `seo_title` / `seo_description` | text | - | D12 |

Indexes come from the browse/search access paths: `(organization_id, status, hidden, launch_at)`, `(category_id)`, `(audience)`, GIN on `search_vector`, trigram on `name` for typo tolerance, `(price_minor, id)` and `(published_at desc, id)` for stable cursor pages. They are verified with `EXPLAIN (ANALYZE, BUFFERS)` in the Phase 2 load test.

`product_tag` (`product_id`, `tag` text 1–40 lower-case; PK both): search and filters.

## product_color
`product_id` (Y), `name` (Y, 1–40, unique per product: "Black"), `hex` (Y, `^#[0-9A-Fa-f]{6}$`), `code` (Y, 2–4 upper-case letters for SKUs: BLK), `swatch_media_id` (-), `sort_order` (Y), `active` (Y).

## product_size
`product_id`, `size_value_id` (PK both), `sort_order`. Size values must belong to the product's size scale (checked in the use case and by trigger).

## product_variant
| Field | Type | Req | Purpose |
| --- | --- | --- | --- |
| `product_id` / `color_id` / `size_value_id` | uuid | Y | unique together |
| `sku` | text, unique per organization, `^[A-Z0-9-]{3,40}$` | Y | "AUR-00001-BLK-M" |
| `barcode` | text, unique per organization where set | - | scanner at packing (staff only) |
| `price_override_minor` / `cost_override_minor` | bigint ≥ 0 | - | per-variant price (e.g. XXL) |
| `weight_grams` | int > 0 | - | |
| `active` | boolean | Y | removed variants are deactivated, never deleted (orders reference them) |

## media_asset
| Field | Type | Req | Purpose |
| --- | --- | --- | --- |
| `storage_key` | text unique | Y | object key in the bucket (D10) |
| `original_file_name` | text ≤ 200 | Y | staff reference only |
| `content_type` | text check (`image/jpeg`,`image/png`,`image/webp`,`image/avif`,`image/svg+xml` for seeds only) | Y | allow-list |
| `byte_size` | int 1–10 485 760 | Y | upload limit |
| `width` / `height` | int, 1–8000 | - until READY | |
| `checksum_sha256` | bytea(32) | - until READY | duplicate detection |
| `status` | text check (`PENDING`,`READY`,`REJECTED`) | Y | |
| `rejection_reason` | text | - | shown to staff |
| `alt_text` | text ≤ 200 | - (required on `product_media` attach) | accessibility |
| `renditions` | jsonb | - | `{ "160": key, "320": key, … }` generated widths |
| `uploaded_by` | uuid → user_account | - | null for seeds |
| `upload_expires_at` | timestamptz | Y | PENDING tickets expire after 15 min; expired assets are cleaned up |

Processing: content type sniffed from the bytes (not trusted from the client), size and dimension limits, decompression-bomb guard, **EXIF and other metadata stripped** (it can contain GPS), re-encoded renditions at 160/320/640/1024/1600 px. A rejected or expired object is deleted from the bucket.

## product_media
`product_id` (Y), `media_asset_id` (Y, must be READY), `color_id` (-, photo per colour), `role` (Y: `PRIMARY`,`BACK`,`DETAIL`,`CUTOUT`,`LIFESTYLE`), `alt_text` (Y), `sort_order` (Y). Unique (`product_id`, `media_asset_id`). Publishing a product (ACTIVE) requires a PRIMARY photo with alt text.

## customer_fit_profile
Defined in [01-identity-access.md](01-identity-access.md); implemented with the size finder in this phase.

## Seed data (≥ 4 rows per table; dev/test/testing/staging only)
Script `db/seed/R__seed_02_catalog.sql`. Fixed UUID prefix `01970000-0000-7000-8002-` + 12 digits (table number + row). Content comes from the prototype (`../aurelle/js/data.js`). **No third-party photos:** the prototype's remote demo images have unclear licences, so seeded `media_asset` rows are simple SVG garment illustrations shipped in `backend/src/main/resources/seed-media/` (one per product type, plus colour swatches).

| Table | Seed rows |
| --- | --- |
| `category` | Tops, Outerwear, Dresses, Bottoms, Footwear (prototype), plus "Kids" with children Kids tops and Kids footwear (tree depth 2), plus "Accessories" (`active = false`) |
| `brand` | Aurelle (every field), Atelier 08, Form & Field, "Old Label" (`active = false`) |
| `product_type` | tee, hoodie, jacket, dress, trousers, sneaker (prototype; sneaker `is_footwear`) |
| `size_scale` | TOPS, WAIST, SHOE_EU, KIDS_AGE, KIDS_SHOE_EU (full reference set) |
| `size_value` | S M L XL · 30 32 34 36 · 40 41 42 43 · 4Y 6Y 8Y 10Y (with age and 80–170 cm height ranges) · 28 30 32 34 |
| `size_guide` | one per scale (5) |
| `product` | the 20 prototype products with their names, audiences, prices and costs (×100 minor units), descriptions and badges; plus: Gilded Evening Dress on sale (`compare_at_price_minor`), Regent Bomber `hidden`, a "Winter capsule coat" DRAFT, a "Retired linen shirt" ARCHIVED, a "Night drop sneaker" with `launch_at` in the future |
| `product_tag` | type, colour and material tags for every product |
| `product_color` | the prototype colours per product (3 each; names and hex from `data.js`) |
| `product_size` | the product's scale values |
| `product_variant` | every colour × size of every product (SKUs `AUR-<number>-<code>-<size>`), one inactive variant, one with a price override |
| `media_asset` | SVG illustrations per product type (6) and 4 swatches, READY; one PENDING and one REJECTED upload for staff screens |
| `product_media` | PRIMARY illustration for every product with alt text; BACK for tees and hoodies |
