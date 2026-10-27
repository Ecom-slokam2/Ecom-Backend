-- Restore the canonical demo catalog rows whose IDs are already referenced by inventory.
-- Product and variant identifiers intentionally match the existing inventory records.
START TRANSACTION;

INSERT INTO products(id, category_id, name, slug, sku, description, brand, ingredients, specifications, pack, type, shelf_life, tags, highlights, about_source, status, created_at, updated_at)
VALUES
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000001'), UUID_TO_BIN('11000000-0000-0000-0000-000000000001'), 'Andhra Mango Avakaya Pickle', 'andhra-mango-avakaya-pickle', 'PKL-ANDHRA-AVAKAYA', 'Raw mango pickle with a bold mustard bite, deep red chilli heat and sesame oil finish.', 'Metaarch Fresh', 'Raw mango, mustard powder, red chilli powder, sesame oil, salt, fenugreek.', 'Pack: 500 g jar\nType: Vegetarian\nShelf life: 6 months\nStorage: Keep in a cool dry place; use a dry spoon.', '500 g', 'Vegetarian', '6 months', 'pickle,avakaya,andhra,mango', 'Traditional Andhra preparation\nStrong mustard flavour\nSmall-batch oil curing', 'Prepared from firm raw mangoes sourced during the early summer pickle season and cured in sesame oil.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000002'), UUID_TO_BIN('11000000-0000-0000-0000-000000000001'), 'Gongura Red Chilli Pickle', 'gongura-red-chilli-pickle', 'PKL-GONGURA-RED', 'Tangy gongura leaves blended with roasted chillies and garlic for a sharp Andhra-style pickle.', 'Metaarch Fresh', 'Gongura leaves, red chillies, garlic, sesame oil, salt, mustard, cumin.', 'Pack: 400 g jar\nType: Vegetarian\nShelf life: 5 months\nTaste: Tangy and spicy.', '400 g', 'Vegetarian', '5 months', 'pickle,gongura,leafy,tangy', 'Sour gongura leaf base\nRoasted chilli aroma\nPairs well with rice and ghee', 'Made with cleaned gongura leaves sourced from regional greens growers and cooked down in small batches.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000003'), UUID_TO_BIN('11000000-0000-0000-0000-000000000001'), 'Garlic Ginger Pickle', 'garlic-ginger-pickle', 'PKL-GARLIC-GINGER', 'Crunchy garlic cloves and ginger pieces pickled in a warm chilli-mustard masala.', 'Metaarch Fresh', 'Garlic, ginger, chilli powder, mustard, lemon juice, oil, salt.', 'Pack: 300 g jar\nType: Vegetarian\nShelf life: 4 months\nTexture: Chunky.', '300 g', 'Vegetarian', '4 months', 'pickle,garlic,ginger,spicy', 'Chunky garlic and ginger pieces\nWarm spice profile\nGood with curd rice and paratha', 'Prepared from peeled garlic and young ginger selected for firmness and clean aroma.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000004'), UUID_TO_BIN('11000000-0000-0000-0000-000000000001'), 'Lemon Green Chilli Pickle', 'lemon-green-chilli-pickle', 'PKL-LEMON-GREEN', 'A bright lemon pickle with slit green chillies, mild bitterness and a lingering citrus heat.', 'Metaarch Fresh', 'Lemon, green chilli, turmeric, mustard, salt, oil, asafoetida.', 'Pack: 350 g jar\nType: Vegetarian\nShelf life: 5 months\nTaste: Citrusy and hot.', '350 g', 'Vegetarian', '5 months', 'pickle,lemon,green-chilli,citrus', 'Citrus-forward flavour\nWhole green chilli pieces\nNaturally tangy finish', 'Made from thin-skinned lemons sorted for juice content and cured until the rind softens.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000005'), UUID_TO_BIN('11000000-0000-0000-0000-000000000001'), 'Tomato Garlic Pickle', 'tomato-garlic-pickle', 'PKL-TOMATO-GARLIC', 'Slow-cooked tomato pickle enriched with garlic and a smoky red chilli tempering.', 'Metaarch Fresh', 'Tomato, garlic, red chilli, tamarind, mustard, sesame oil, salt.', 'Pack: 450 g jar\nType: Vegetarian\nShelf life: 4 months\nTexture: Thick relish.', '450 g', 'Vegetarian', '4 months', 'pickle,tomato,garlic,relish', 'Rich tomato reduction\nGarlic-forward taste\nSpreadable texture', 'Cooked from ripe tomatoes sourced from local vegetable mandis and reduced for a thick homemade texture.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000006'), UUID_TO_BIN('11000000-0000-0000-0000-000000000002'), 'California Almonds', 'california-almonds-250g', 'DRY-CAL-ALMOND', 'Crisp whole almonds with a clean nutty flavour for snacking, soaking or desserts.', 'Metaarch Select', 'Whole almonds.', 'Pack: 250 g pouch\nType: Vegetarian\nShelf life: 9 months\nGrade: Whole kernels.', '250 g', 'Vegetarian', '9 months', 'dry-fruits,almonds,nuts', 'Whole almond kernels\nNaturally crunchy\nGood for daily snacking', 'Sourced from calibrated almond lots and packed in moisture-protected pouches.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000007'), UUID_TO_BIN('11000000-0000-0000-0000-000000000002'), 'Premium Cashew Nuts', 'premium-cashew-nuts-250g', 'DRY-PREM-CASHEW', 'Creamy cashew kernels suited for sweets, curries, roasting and gifting boxes.', 'Metaarch Select', 'Whole cashew kernels.', 'Pack: 250 g pouch\nType: Vegetarian\nShelf life: 8 months\nGrade: W320 style whole kernels.', '250 g', 'Vegetarian', '8 months', 'dry-fruits,cashew,nuts', 'Creamy bite\nMostly whole kernels\nReady for roasting or cooking', 'Packed from sorted cashew kernels selected for colour, size and low breakage.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000008'), UUID_TO_BIN('11000000-0000-0000-0000-000000000002'), 'Afghan Raisins', 'afghan-raisins', 'DRY-AFG-RAISIN', 'Naturally sweet raisins with soft chew and golden-green colour for cereals and sweets.', 'Metaarch Select', 'Dried grapes.', 'Pack: 200 g pouch\nType: Vegetarian\nShelf life: 7 months\nTexture: Soft dried fruit.', '200 g', 'Vegetarian', '7 months', 'dry-fruits,raisins,dried-fruit', 'Naturally sweet\nSoft chewy texture\nNo added sugar', 'Selected from dried grape lots and packed after visual sorting for stems and foreign matter.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000009'), UUID_TO_BIN('11000000-0000-0000-0000-000000000002'), 'Iranian Pistachios', 'iranian-pistachios', 'DRY-IRN-PISTA', 'Lightly open-shell pistachios with rich flavour for premium snacking and desserts.', 'Metaarch Select', 'Pistachios in shell.', 'Pack: 200 g pouch\nType: Vegetarian\nShelf life: 8 months\nStyle: Open-shell.', '200 g', 'Vegetarian', '8 months', 'dry-fruits,pistachio,nuts', 'Open-shell pistachios\nRich savoury nut flavour\nGreat for desserts', 'Sourced from graded pistachio lots and sealed to retain crunch during storage.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000010'), UUID_TO_BIN('11000000-0000-0000-0000-000000000002'), 'Premium Walnut Kernels', 'premium-walnut-kernels', 'DRY-PREM-WALNUT', 'Light brown walnut kernels with earthy flavour for baking, breakfast bowls and snacks.', 'Metaarch Select', 'Walnut kernels.', 'Pack: 200 g pouch\nType: Vegetarian\nShelf life: 6 months\nStorage: Refrigerate after opening.', '200 g', 'Vegetarian', '6 months', 'dry-fruits,walnut,kernels', 'Clean walnut halves and pieces\nEarthy rich flavour\nUseful for baking', 'Packed from sorted walnut kernels checked for shell fragments and freshness.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000011'), UUID_TO_BIN('11000000-0000-0000-0000-000000000003'), 'Fresh Tomatoes', 'fresh-tomatoes', 'VEG-FRESH-TOMATO', 'Firm red tomatoes suitable for curries, chutneys, salads and everyday cooking.', 'Metaarch Farm', 'Fresh tomatoes.', 'Pack: 1 kg\nType: Vegetarian\nShelf life: 3 days\nStorage: Keep cool and dry.', '1 kg', 'Vegetarian', '3 days', 'vegetables,tomato,fresh', 'Firm ripe tomatoes\nSorted for visible damage\nUseful for daily cooking', 'Sourced from local vegetable markets and sorted before dispatch.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000012'), UUID_TO_BIN('11000000-0000-0000-0000-000000000003'), 'Fresh Potatoes', 'fresh-potatoes', 'VEG-FRESH-POTATO', 'Clean medium potatoes for fries, curries, mash and home-style snacks.', 'Metaarch Farm', 'Fresh potatoes.', 'Pack: 1 kg\nType: Vegetarian\nShelf life: 10 days\nStorage: Store away from sunlight.', '1 kg', 'Vegetarian', '10 days', 'vegetables,potato,root', 'Medium-sized tubers\nMulti-purpose cooking\nSorted for cuts and sprouts', 'Procured from potato traders and checked for firmness before packing.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000013'), UUID_TO_BIN('11000000-0000-0000-0000-000000000003'), 'Green Chilli', 'green-chilli', 'VEG-GREEN-CHILLI', 'Fresh green chillies with sharp heat for tadka, chutneys and pickles.', 'Metaarch Farm', 'Fresh green chillies.', 'Pack: 250 g\nType: Vegetarian\nShelf life: 5 days\nTaste: Hot.', '250 g', 'Vegetarian', '5 days', 'vegetables,chilli,spicy', 'Bright green colour\nSharp heat\nGood for tempering', 'Sourced from chilli growers and packed after removing visibly damaged pods.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000014'), UUID_TO_BIN('11000000-0000-0000-0000-000000000003'), 'Fresh Carrots', 'fresh-carrots', 'VEG-FRESH-CARROT', 'Crunchy orange carrots for salads, curries, soups and juices.', 'Metaarch Farm', 'Fresh carrots.', 'Pack: 500 g\nType: Vegetarian\nShelf life: 6 days\nTexture: Crunchy.', '500 g', 'Vegetarian', '6 days', 'vegetables,carrot,fresh', 'Crunchy roots\nNaturally sweet taste\nWashed and sorted', 'Selected from fresh carrot arrivals and packed by size for easier cooking.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000015'), UUID_TO_BIN('11000000-0000-0000-0000-000000000003'), 'Fresh Lady Finger', 'fresh-lady-finger', 'VEG-LADY-FINGER', 'Tender okra pods for stir-fries, curries and crispy bhindi recipes.', 'Metaarch Farm', 'Fresh lady finger.', 'Pack: 500 g\nType: Vegetarian\nShelf life: 4 days\nTexture: Tender pods.', '500 g', 'Vegetarian', '4 days', 'vegetables,okra,bhindi', 'Tender pods\nGood for fry and curry\nSorted for over-mature pieces', 'Procured from daily okra arrivals and packed to reduce bruising.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000016'), UUID_TO_BIN('11000000-0000-0000-0000-000000000003'), 'Fresh Brinjal', 'fresh-brinjal', 'VEG-FRESH-BRINJAL', 'Glossy purple brinjal for stuffed curries, sambar and roasted preparations.', 'Metaarch Farm', 'Fresh brinjal.', 'Pack: 500 g\nType: Vegetarian\nShelf life: 4 days\nStyle: Small to medium brinjals.', '500 g', 'Vegetarian', '4 days', 'vegetables,brinjal,eggplant', 'Glossy purple skin\nSoft cooking texture\nGood for stuffed curry', 'Sorted from fresh brinjal lots for firmness and minimal surface marks.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000017'), UUID_TO_BIN('11000000-0000-0000-0000-000000000003'), 'Fresh Spinach', 'fresh-spinach', 'VEG-FRESH-SPINACH', 'Leafy spinach bunches for dal, palak curry, soups and smoothies.', 'Metaarch Farm', 'Fresh spinach leaves.', 'Pack: 1 bunch\nType: Vegetarian\nShelf life: 2 days\nStorage: Refrigerate after delivery.', '1 bunch', 'Vegetarian', '2 days', 'vegetables,spinach,leafy-greens', 'Fresh green leaves\nGood for dal and curries\nCleaned of visible wilted leaves', 'Sourced from leafy greens vendors and packed for same-day use.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000018'), UUID_TO_BIN('11000000-0000-0000-0000-000000000004'), 'Chicken Curry Cut', 'chicken-curry-cut', 'NV-CHICKEN-CURRY', 'Bone-in chicken pieces cut for curry, biryani and home-style gravy dishes.', 'Metaarch Fresh Meat', 'Fresh chicken, bone-in pieces.', 'Pack: 500 g\nType: Non-vegetarian\nShelf life: Use within 24 hours\nStorage: Keep refrigerated.', '500 g', 'Non-vegetarian', '24 hours', 'non-veg,chicken,curry-cut', 'Bone-in curry pieces\nCleaned and packed chilled\nSuitable for gravies', 'Processed from fresh poultry batches and packed under chilled handling conditions.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000019'), UUID_TO_BIN('11000000-0000-0000-0000-000000000004'), 'Chicken Boneless', 'chicken-boneless', 'NV-CHICKEN-BONELESS', 'Boneless chicken cubes for starters, curries, noodles and quick pan cooking.', 'Metaarch Fresh Meat', 'Fresh boneless chicken.', 'Pack: 500 g\nType: Non-vegetarian\nShelf life: Use within 24 hours\nCut: Boneless cubes.', '500 g', 'Non-vegetarian', '24 hours', 'non-veg,chicken,boneless', 'Boneless pieces\nQuick to cook\nPacked chilled', 'Cut from fresh chicken and trimmed for convenient everyday cooking.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000020'), UUID_TO_BIN('11000000-0000-0000-0000-000000000004'), 'Chicken Breast', 'chicken-breast', 'NV-CHICKEN-BREAST', 'Lean chicken breast portions for grilling, salads, sandwiches and meal prep.', 'Metaarch Fresh Meat', 'Fresh chicken breast.', 'Pack: 450 g\nType: Non-vegetarian\nShelf life: Use within 24 hours\nCut: Breast fillet.', '450 g', 'Non-vegetarian', '24 hours', 'non-veg,chicken,breast,lean', 'Lean breast portions\nGood for grilling\nLow-bone cut', 'Trimmed from fresh chicken and packed as lean portions for faster prep.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000021'), UUID_TO_BIN('11000000-0000-0000-0000-000000000004'), 'Mutton Curry Cut', 'mutton-curry-cut', 'NV-MUTTON-CURRY', 'Bone-in mutton pieces cut for slow-cooked curries and biryani gravies.', 'Metaarch Fresh Meat', 'Fresh mutton, bone-in pieces.', 'Pack: 500 g\nType: Non-vegetarian\nShelf life: Use within 24 hours\nCut: Curry pieces.', '500 g', 'Non-vegetarian', '24 hours', 'non-veg,mutton,curry-cut', 'Bone-in mutton pieces\nIdeal for slow cooking\nPacked chilled', 'Sourced from inspected meat vendors and portioned for curry-style cooking.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000022'), UUID_TO_BIN('11000000-0000-0000-0000-000000000004'), 'Fresh Fish Fillet', 'fresh-fish-fillet', 'NV-FISH-FILLET', 'Clean fish fillets for pan-fry, curry, grilling and coastal-style recipes.', 'Metaarch Fresh Seafood', 'Fresh fish fillet.', 'Pack: 400 g\nType: Non-vegetarian\nShelf life: Use within 24 hours\nCut: Fillet.', '400 g', 'Non-vegetarian', '24 hours', 'non-veg,fish,fillet,seafood', 'Clean fillets\nNo curry bones\nGood for fry and grill', 'Prepared from fresh fish lots and packed chilled for same-day cooking.', 'ACTIVE', NOW(6), NOW(6)),
  (UUID_TO_BIN('21000000-0000-0000-0000-000000000023'), UUID_TO_BIN('11000000-0000-0000-0000-000000000004'), 'Fresh Prawns', 'fresh-prawns', 'NV-FRESH-PRAWNS', 'Cleaned prawns for fry, curry, pulao and quick seafood starters.', 'Metaarch Fresh Seafood', 'Fresh prawns.', 'Pack: 250 g\nType: Non-vegetarian\nShelf life: Use within 24 hours\nCut: Cleaned prawns.', '250 g', 'Non-vegetarian', '24 hours', 'non-veg,prawns,seafood', 'Cleaned prawns\nQuick cooking\nPacked chilled', 'Sourced from fresh seafood vendors and cleaned before chilled packing.', 'ACTIVE', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE id = id;

-- Restore the canonical catalog rows referenced by existing inventory. INSERT IGNORE
-- makes this safe to apply when any canonical rows already exist and preserves them.

INSERT IGNORE INTO product_variants(id, product_id, sku, name, attributes, price, compare_at_price, active, created_at, updated_at)
SELECT UUID_TO_BIN(v.variant_id), p.id,
       CONCAT(p.sku, '-', REPLACE(UPPER(p.pack), ' ', '')),
       p.pack, JSON_OBJECT('pack', p.pack, 'type', p.type), prices.price, prices.compare_at_price,
       TRUE, NOW(6), NOW(6)
FROM (
  SELECT 'PKL-ANDHRA-AVAKAYA' sku, '0214d83e-d138-11f1-8439-74563cbdba97' variant_id UNION ALL
  SELECT 'PKL-GONGURA-RED', '0214d8ed-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'PKL-GARLIC-GINGER', '0214d917-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'PKL-LEMON-GREEN', '0214d939-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'PKL-TOMATO-GARLIC', '0214d96a-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'DRY-CAL-ALMOND', '0214d98c-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'DRY-PREM-CASHEW', '0214d9ac-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'DRY-AFG-RAISIN', '0214d9ca-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'DRY-IRN-PISTA', '0214d9e9-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'DRY-PREM-WALNUT', '0214da08-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'VEG-FRESH-TOMATO', '0214da2a-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'VEG-FRESH-POTATO', '0214da4a-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'VEG-GREEN-CHILLI', '0214da6a-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'VEG-FRESH-CARROT', '0214da88-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'VEG-LADY-FINGER', '0214daac-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'VEG-FRESH-BRINJAL', '0214daca-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'VEG-FRESH-SPINACH', '0214daeb-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'NV-CHICKEN-CURRY', '0214db0d-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'NV-CHICKEN-BONELESS', '0214db2d-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'NV-CHICKEN-BREAST', '0214db4c-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'NV-MUTTON-CURRY', '0214db6b-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'NV-FISH-FILLET', '0214db91-d138-11f1-8439-74563cbdba97' UNION ALL
  SELECT 'NV-FRESH-PRAWNS', '0214dbb1-d138-11f1-8439-74563cbdba97'
) v
JOIN products p ON p.sku = v.sku
JOIN (
  SELECT 'PKL-ANDHRA-AVAKAYA' sku, 289.00 price, 329.00 compare_at_price UNION ALL
  SELECT 'PKL-GONGURA-RED', 249.00, 289.00 UNION ALL
  SELECT 'PKL-GARLIC-GINGER', 199.00, 239.00 UNION ALL
  SELECT 'PKL-LEMON-GREEN', 179.00, 219.00 UNION ALL
  SELECT 'PKL-TOMATO-GARLIC', 229.00, 269.00 UNION ALL
  SELECT 'DRY-CAL-ALMOND', 299.00, 349.00 UNION ALL
  SELECT 'DRY-PREM-CASHEW', 359.00, 419.00 UNION ALL
  SELECT 'DRY-AFG-RAISIN', 169.00, 199.00 UNION ALL
  SELECT 'DRY-IRN-PISTA', 399.00, 459.00 UNION ALL
  SELECT 'DRY-PREM-WALNUT', 329.00, 389.00 UNION ALL
  SELECT 'VEG-FRESH-TOMATO', 42.00, 48.00 UNION ALL
  SELECT 'VEG-FRESH-POTATO', 38.00, 44.00 UNION ALL
  SELECT 'VEG-GREEN-CHILLI', 28.00, 34.00 UNION ALL
  SELECT 'VEG-FRESH-CARROT', 54.00, 62.00 UNION ALL
  SELECT 'VEG-LADY-FINGER', 46.00, 52.00 UNION ALL
  SELECT 'VEG-FRESH-BRINJAL', 44.00, 50.00 UNION ALL
  SELECT 'VEG-FRESH-SPINACH', 24.00, 30.00 UNION ALL
  SELECT 'NV-CHICKEN-CURRY', 169.00, 189.00 UNION ALL
  SELECT 'NV-CHICKEN-BONELESS', 249.00, 279.00 UNION ALL
  SELECT 'NV-CHICKEN-BREAST', 239.00, 269.00 UNION ALL
  SELECT 'NV-MUTTON-CURRY', 499.00, 549.00 UNION ALL
  SELECT 'NV-FISH-FILLET', 299.00, 339.00 UNION ALL
  SELECT 'NV-FRESH-PRAWNS', 229.00, 259.00
) prices ON prices.sku = v.sku;

INSERT IGNORE INTO store_products(id, store_id, store_name, product_id, available, price_override, created_at, updated_at)
SELECT UUID_TO_BIN(UUID()), 'store-gachibowli', 'Thejo Gachibowli Store', p.id, TRUE, NULL, NOW(6), NOW(6)
FROM products p
WHERE p.sku IN (
  'PKL-ANDHRA-AVAKAYA','PKL-GONGURA-RED','PKL-GARLIC-GINGER','PKL-LEMON-GREEN','PKL-TOMATO-GARLIC',
  'DRY-CAL-ALMOND','DRY-PREM-CASHEW','DRY-AFG-RAISIN','DRY-IRN-PISTA','DRY-PREM-WALNUT',
  'VEG-FRESH-TOMATO','VEG-FRESH-POTATO','VEG-GREEN-CHILLI','VEG-FRESH-CARROT','VEG-LADY-FINGER',
  'VEG-FRESH-BRINJAL','VEG-FRESH-SPINACH','NV-CHICKEN-CURRY','NV-CHICKEN-BONELESS','NV-CHICKEN-BREAST',
  'NV-MUTTON-CURRY','NV-FISH-FILLET','NV-FRESH-PRAWNS'
);

UPDATE products SET featured = TRUE
WHERE sku IN ('PKL-ANDHRA-AVAKAYA', 'DRY-CAL-ALMOND', 'VEG-FRESH-TOMATO', 'NV-CHICKEN-CURRY');
UPDATE products SET special = TRUE
WHERE sku IN ('PKL-GONGURA-RED', 'DRY-PREM-CASHEW', 'PKL-GARLIC-GINGER', 'NV-MUTTON-CURRY');
UPDATE products SET new_arrival = TRUE
WHERE sku IN ('PKL-TOMATO-GARLIC', 'DRY-IRN-PISTA', 'VEG-FRESH-SPINACH', 'NV-FISH-FILLET');
UPDATE products SET best_seller = TRUE
WHERE sku IN ('PKL-LEMON-GREEN', 'DRY-AFG-RAISIN', 'VEG-FRESH-CARROT', 'NV-FRESH-PRAWNS');

UPDATE products
SET global_product = TRUE, fulfillment_type = 'global', delivery_method = 'global'
WHERE sku IN ('DRY-CAL-ALMOND','DRY-PREM-CASHEW','DRY-AFG-RAISIN','DRY-IRN-PISTA','DRY-PREM-WALNUT');

INSERT IGNORE INTO store_products(id, store_id, store_name, product_id, available, price_override, created_at, updated_at)
SELECT UUID_TO_BIN(UUID()), 'global-store', 'India-wide Global Store', p.id, TRUE, NULL, NOW(6), NOW(6)
FROM products p
WHERE p.sku IN ('DRY-CAL-ALMOND','DRY-PREM-CASHEW','DRY-AFG-RAISIN','DRY-IRN-PISTA','DRY-PREM-WALNUT');

COMMIT;
