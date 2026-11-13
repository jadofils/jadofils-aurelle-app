# Phase 2 — Catalog and media (status: draft)

Modules: `catalog`, `media`.

## category (hierarchical)
`parent_id` (-), `name` (Y), `slug` unique (Y), `description` (-), `image_media_id` (-), `sort_order` (Y, default 0), `active` (Y), `seo_title`/`seo_description` (-). Prototype: Tops, Outerwear, Dresses, Bottoms, Footwear.

## brand
`name` (Y), `slug` unique (Y), `logo_media_id` (-), `description` (-), `active` (Y). Prototype mock API: Aurelle, Atelier 08, Form & Field.

## product_type (lookup)
`key` unique (`tee`,`hoodie`,`jacket`,`dress`,`trousers`,`sneaker`,…), `name`, `illustration_key` (-, for the prototype-style flat art fallback), `default_size_scale_id` (-), `is_footwear` (Y, shoe wall / shoe group).

## size_scale / size_value
- `size_scale`: `key` (`TOPS`,`WAIST`,`SHOE_EU`,`KIDS_AGE`,`KIDS_SHOE_EU`), `name`.
- `size_value`: `size_scale_id`, `label` ("M", "32", "41", "6Y"), `sort_order`, `age_min`/`age_max` (-), `height_min_cm`/`height_max_cm` (-), `chest_cm`/`waist_cm`/`foot_length_mm` (-). Powers the size finder.

## product
| Field | Type | Req | Purpose |
| --- | --- | --- | --- |
| `slug` | citext unique | Y | shareable URL |
| `name` | text | Y | "Noir Silk Tee" |
| `subtitle` | text | - | one-line hook on cards |
| `description` | text (markdown) | Y | |
| `product_type_id` / `category_id` | uuid | Y | |
| `brand_id` | uuid | - | |
| `audience` | text check (`MEN`,`WOMEN`,`BOYS`,`GIRLS`,`KIDS_UNISEX`,`UNISEX`) | Y | audience tabs |
| `status` | text check (`DRAFT`,`ACTIVE`,`ARCHIVED`) | Y | |
| `hidden` | boolean | Y | prototype `hidden`; temporarily off the storefront |
| `badge` | text | - | "New", "Best seller", "Limited" |
| `price_minor` | bigint ≥ 0 | Y | |
| `compare_at_price_minor` | bigint | - | sale "was" price; enables price-drop alerts |
| `cost_minor` | bigint | - | **staff only** (`catalog.product.read_cost`) |
| `currency` | char(3) | Y | |
| `material` / `composition` | text | - | "silk-cotton", "95% cotton 5% elastane" |
| `care_instructions` | text | - | |
| `fit_notes` | text | - | "Cut slightly long" |
| `size_scale_id` | uuid | Y | |
| `size_guide_id` | uuid | - | |
| `country_of_origin` | char(2) | - | |
| `collection` / `season` | text | - | "Summer drop" |
| `weight_grams` | int | - | shipping cost |
| `hs_code` / `tax_class` | text | - | customs / tax (D11) |
| `launch_at` | timestamptz | - | scheduled drop; hidden until then |
| `published_at` | timestamptz | - | |
| `rating_average` / `rating_count` | numeric(2,1) / int | - | denormalised from reviews (Phase 7) |
| `search_vector` | tsvector (generated) | Y | name, subtitle, material, tags, colour names |
| `seo_title` / `seo_description` | text | - | |

`product_tag` (`product_id`, `tag`) for search and filters.

## product_color
`product_id` (Y), `name` (Y, "Black"), `hex` (Y, `#151515`), `swatch_media_id` (-), `sort_order` (Y), `active` (Y). Unique (`product_id`, `name`).

## product_size
`product_id`, `size_value_id`, `sort_order`. The sizes the product is offered in.

## product_variant
| Field | Type | Req | Purpose |
| --- | --- | --- | --- |
| `product_id` / `color_id` / `size_value_id` | uuid | Y | unique together |
| `sku` | text unique | Y | "AUR-00001-BLK-M" |
| `barcode` | text unique (partial) | - | scanner at packing |
| `price_override_minor` / `cost_override_minor` | bigint | - | |
| `weight_grams` | int | - | |
| `active` | boolean | Y | |

## media_asset
`storage_key` (Y), `content_type` (Y, allow-list jpeg/png/webp/avif), `byte_size` (Y, limit), `width`/`height` (Y after processing), `checksum_sha256` (Y), `status` (`PENDING`,`READY`,`REJECTED`), `alt_text` (- on upload, **Y before a product is published**), `uploaded_by` (Y), `variants` jsonb (generated sizes/thumbnails).

## product_media
`product_id` (Y), `color_id` (-, photo per colour), `media_id` (Y), `role` (`PRIMARY`,`BACK`,`DETAIL`,`CUTOUT`,`LIFESTYLE`) — `BACK` powers the turntable's "real back", `CUTOUT` (transparent PNG) powers colour tinting, `sort_order`.

## size_guide
`name`, `size_scale_id`, `body` (markdown), rows from `size_value` measurements, `how_to_measure_media_id` (-).
