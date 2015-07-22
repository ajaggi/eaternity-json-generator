package ch.generator;

import com.google.common.collect.Iterables;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.*;

public class BatchGenerator {

	// ATTENTION These whole setting work just until 500 baseproducts!
	// Max AMOUNT_INGREDIENTS_PER_RECIPE = PERCENTAGE_BASE_INGREDIENTS * 500 / 100

	public static final int YEAR = 2014;
	public static final int MONTH = 6;

	public static final int AMOUNT_SUPPLIES = 22;
	public static final int AMOUNT_RECIPES = 40;
	public static final int AMOUNT_TRANSIENT = 0;
	// if you change this, also change INGREDIENT_WEIGHT_RANGE_RECIPES to get a couple of climate friendly recipe
	public static final int AMOUNT_INGREDIENTS_PER_RECIPE = 10;
	public static final int AMOUNT_INGREDIENTS_PER_SUPPLY = 50;
	public static final int AMOUNT_INGREDIENTS_TRANSIENT = 4000;
	public static final int PERCENTAGE_DIFFERENT_ORIGINS = 100;
	public static final int PERCENTAGE_DIFFERENT_MITEMS = 100; // that means the percentage value are all different mItems, then it repeats

	// TODO not implemented yet the dependance on this!
	public static final int PERCENTAGE_MITEMS_CHANGED = 25; // this is maximum the PERCENTAGE_DIFFERENT_MITEMS

	// Here we can specify the probability of the different dimensional
	// Ingredients
	private static final int PERCENTAGE_BASE_INGREDIENTS = 50;
	private static final int PERCENTAGE_TWO_DIM_INGREDIENTS = 30;
	private static final int PERCENTAGE_THREE_DIM_INGREDIENTS = 20;

	private static final int TWO_DIMENSIONAL_BASE_NUMBER = 10000;
	private static final int THREE_DIMENSIONAL_BASE_NUMBER = 20000;

	private static final boolean REAL_MATCHING_ITEMS = false;
	private int totalAmountIngredients = AMOUNT_TRANSIENT * AMOUNT_INGREDIENTS_TRANSIENT + (AMOUNT_RECIPES - AMOUNT_TRANSIENT) * AMOUNT_INGREDIENTS_PER_RECIPE;

	private final List<Integer> baseProductIds = getBaseProductIds(900 * PERCENTAGE_BASE_INGREDIENTS / 100 + 1);
	private final List<Integer> twoDimProductIds = getHigherDimProductIds(900 * PERCENTAGE_TWO_DIM_INGREDIENTS / 100 + 1, TWO_DIMENSIONAL_BASE_NUMBER);
	private final List<Integer> threeDimProductIds = getHigherDimProductIds(900 * PERCENTAGE_THREE_DIM_INGREDIENTS / 100 + 1, THREE_DIMENSIONAL_BASE_NUMBER);

	private List<Integer> productIds = new ArrayList<Integer>();
	private List<String> countries = new ArrayList<String>();
	private List<String> menuNames = getMenuNames();
	private Iterator<String> menuNamesIterator = Iterables.cycle(menuNames).iterator();

	private static final int INGREDIENT_WEIGHT_RANGE_RECIPES = 80;
	private static final int INGREDIENT_WEIGHT_RANGE_SUPPLIES = 6000;

	private static final String[] TRANSPORATION_MODES = new String[] { "air", "ground", "sea", "train" };
	private static final String[] PRODUCTION_MODES = new String[] { "standard", " organic", "fair-trade", "greenhouse", " farm", "wild-caught" };
	private static final String[] PROCESSING_MODES = new String[] { "raw", "unboned", "boned", "skinned", "beheaded", "fillet", "cut", "boiled", "peeled" };
	private static final String[] CONSERVATION_MODES = new String[] { "fresh", "frozen", "dried", "conserved", "canned", "boiled-down" };
	private static final String[] PACKAGING_MODES = new String[] { "plastic", "paper", "pet", "tin", "alu", "glas", "cardboard", "tetra" };
	private static final String[] INGREDIENT_NAMES = getIngredientNames();

	private Random rand = new Random();
	private Map<Integer, String> matchingItemIdsAndNames;

	public BatchGenerator() throws IOException {
		if (AMOUNT_TRANSIENT > AMOUNT_RECIPES)
			throw new IllegalArgumentException("Amount transient recipes can not be bigger than total amount recipes");
		ArrayList<Integer> allProductIds = getAllProductIds();

		// add 2 because 1 for rounding and 1 for exclusion of end index.
		countries = getCountryNames().subList(0, Math.min((PERCENTAGE_DIFFERENT_ORIGINS * totalAmountIngredients / 100 + 2), getCountryNames().size()));
		productIds = new ArrayList<Integer>(allProductIds.subList(0, Math.min(PERCENTAGE_DIFFERENT_MITEMS * totalAmountIngredients / 100 + 2, allProductIds.size())));

		// repeat the products in the list so that PERCENTAGE_DIFFERENT_MITEMS
		// is correct
		while (productIds.size() < totalAmountIngredients) {
			productIds.addAll(productIds);
		}

		productIds = productIds.subList(0, totalAmountIngredients + 2);
	}

	/**
	 * Generate a recipe batch json with AMOUNT_INGREDIENTS_PER_RECIPE recipes and
	 * AMOUNT_INGREDIENTS_PER_RECIPE or AMOUNT_INGREDIENTS_TRANSIENT ingredients each.
	 * 
	 */
	public void generateRecipeJSON() {
		System.out.println("Amount of recipes: " + AMOUNT_RECIPES);
		System.out.println("Amount of ingredients: " + totalAmountIngredients);

		int counter = AMOUNT_TRANSIENT;

		String batchRecipesJson = "[";

		for (int i = 0; i < AMOUNT_RECIPES; i++) {
			batchRecipesJson += "{	\"request-id\": " + i + ",";
			if (counter > 0) {
				batchRecipesJson += "\"transient\": " + "true" + ",";
				batchRecipesJson += generateCompositeRootJson(AMOUNT_INGREDIENTS_TRANSIENT, "recipe") + "}";
				counter--;
			} else
				batchRecipesJson += generateCompositeRootJson(AMOUNT_INGREDIENTS_PER_RECIPE, "recipe") + "}";

			if (i < AMOUNT_RECIPES - 1)
				batchRecipesJson += ",\n";
		}

		batchRecipesJson += "]";

		writeFile("batch_" + AMOUNT_RECIPES + "_recipes_" + totalAmountIngredients + "_ingredient.json", batchRecipesJson);
	}

	public void generateSupplyJSON() {
		System.out.println("Amount of supplies: " + AMOUNT_SUPPLIES);
		System.out.println("Amount of ingredients: " + totalAmountIngredients);

		String batchRecipesJson = "[";

		for (int i = 0; i < AMOUNT_SUPPLIES; i++) {
			batchRecipesJson += "{	\"request-id\": " + i + ",";
			batchRecipesJson += generateCompositeRootJson(AMOUNT_INGREDIENTS_PER_SUPPLY, "supply") + "}";

			if (i < AMOUNT_SUPPLIES - 1)
				batchRecipesJson += ",\n";
		}

		batchRecipesJson += "]";

		writeFile("batch_" + AMOUNT_SUPPLIES + "_supplies_" + totalAmountIngredients + "_ingredient.json", batchRecipesJson);
	}

	// **************************************************

	private String generateCompositeRootJson(Integer numberOfIngredients, String kindOfcompositeRoot) {
		List<Integer> productIdsCopy = new ArrayList<Integer>(productIds);
		
		String compositeRootJSON = getContentFromFile(kindOfcompositeRoot + ".json");
		if (kindOfcompositeRoot.equals("supply"))
			compositeRootJSON += "\"supply-date\": ";
		else
			compositeRootJSON += "\"date\": ";

		compositeRootJSON += "\"" + YEAR + "-" + MONTH + "-" + generateRandomDay() + "\",";

		if (kindOfcompositeRoot.equals("recipe")) {
			compositeRootJSON += "\"title\": \"" + menuNamesIterator.next() + "\",";
		}

		compositeRootJSON += "	\"ingredients\": [\n";
		for (int i = 0; i < numberOfIngredients; i++) {
			compositeRootJSON += generateIngredientJSON(kindOfcompositeRoot,productIdsCopy);
			if (i < numberOfIngredients - 1)
				compositeRootJSON += ",\n";
		}
		compositeRootJSON += "] }";
		return compositeRootJSON;
	}

	private String generateIngredientJSON(String kindOfcompositeRoot, List<Integer> productIdsCopy) {
		int weightRange = INGREDIENT_WEIGHT_RANGE_RECIPES;
		if (kindOfcompositeRoot.equals("supply"))
			weightRange = INGREDIENT_WEIGHT_RANGE_SUPPLIES;
		
		
		String ingredientJSON = "{";
		int index = rand.nextInt(productIdsCopy.size());
		ingredientJSON += "\"id\": \"" + productIdsCopy.get(index) + "\",";
		String ingredientName;
		if (REAL_MATCHING_ITEMS) {
			ingredientName = matchingItemIdsAndNames.get(productIdsCopy.get(index));
		} else {
			ingredientName = INGREDIENT_NAMES[rand.nextInt(INGREDIENT_NAMES.length)];
		}
		ingredientJSON += "\"name\": \"" + ingredientName + "\",";
		ingredientJSON += "\"origin\": \"" + countries.get(rand.nextInt(countries.size())) + "\",";
		ingredientJSON += "\"amount\": " + rand.nextInt(weightRange) + ",";
		ingredientJSON += "\"transport\": \"" + TRANSPORATION_MODES[rand.nextInt(TRANSPORATION_MODES.length)] + "\",";
		ingredientJSON += "\"production\": \"" + PRODUCTION_MODES[rand.nextInt(PRODUCTION_MODES.length)] + "\",";
		ingredientJSON += "\"processing\": \"" + PROCESSING_MODES[rand.nextInt(PROCESSING_MODES.length)] + "\",";
		ingredientJSON += "\"conservation\": \"" + CONSERVATION_MODES[rand.nextInt(CONSERVATION_MODES.length)] + "\",";
		ingredientJSON += "\"packaging\": \"" + PACKAGING_MODES[rand.nextInt(PACKAGING_MODES.length)] + "\",";
		ingredientJSON += getContentFromFile("ingredient.json");
		ingredientJSON += "}";

		// remove the product from the list so that its not used twice...
		productIdsCopy.remove(index);

		return ingredientJSON;
	}

	private void writeFile(String filename, String content) {
		try {
			File file = new File(filename);

			// if file doesnt exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			}

			OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(file.getAbsoluteFile()), "UTF-8");
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(content);
			bw.close();

			System.out.println("File successfull Written: " + filename);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String getContentFromFile(String filename) {
		String content = "";
		BufferedReader bufferedReader = null;
		try {
			String sCurrentLine;
			bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"));

			while ((sCurrentLine = bufferedReader.readLine()) != null) {
				content = content + sCurrentLine;
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (bufferedReader != null)
					bufferedReader.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		return content;
	}

	private ArrayList<Integer> getAllProductIds() throws IOException {
		ArrayList<Integer> productIds = new ArrayList<>();
		if (REAL_MATCHING_ITEMS)
			productIds.addAll(new ArrayList<Integer>(getRealMatchingItemIdsAndNames().keySet()));
		else {
			productIds.addAll(baseProductIds);
			productIds.addAll(twoDimProductIds);
			productIds.addAll(threeDimProductIds);
		}

		// shuffle the ids
		ArrayList<Integer> randomProductIds = new ArrayList<Integer>();
		int index = 0;
		for (int i = 0; i < productIds.size(); i++) {
			index = rand.nextInt(productIds.size());
			randomProductIds.add(productIds.get(index));
			productIds.remove(index);
		}

		return randomProductIds;
	}

	private List<Integer> getBaseProductIds(int amountOfProducts) {
		return getBaseProductIds().subList(0, amountOfProducts);
	}

	private List<Integer> getHigherDimProductIds(int numberOfHigherDimMatchingItems, int idBaseNumber) {
		List<Integer> returnList = new ArrayList<Integer>();
		String higherDimStringIds = "";
		for (int i = 0; i < numberOfHigherDimMatchingItems - 1; i++) {
			higherDimStringIds += Integer.toString(idBaseNumber) + Integer.toString(i) + ", ";
		}
		higherDimStringIds += Integer.toString(idBaseNumber) + Integer.toString(numberOfHigherDimMatchingItems);

		String namesSplitted[] = higherDimStringIds.split(",");
		for (String name : namesSplitted) {
			returnList.add(Integer.parseInt(name.trim()));
		}
		return returnList;
	}

	private List<String> getCountryNames() {
		// String names =
		// "United States of America, Afghanistan, Albania, Algeria, Andorra, Angola, Antigua & Deps, Argentina, Armenia, Australia, Austria, Azerbaijan, Bahamas, Bahrain, Bangladesh, Barbados, Belarus, Belgium, Belize, Benin, Bhutan, Bolivia, Bosnia Herzegovina, Botswana, Brazil, Brunei, Bulgaria, Burkina, Burma, Burundi, Cambodia, Cameroon, Canada, Cape Verde, Central African Rep, Chad, Chile, People's Republic of China, Republic of China, Colombia, Comoros, Democratic Republic of the Congo, Republic of the Congo, Costa Rica,, Croatia, Cuba, Cyprus, Czech Republic, Danzig, Denmark, Djibouti, Dominica, Dominican Republic, East Timor, Ecuador, Egypt, El Salvador, Equatorial Guinea, Eritrea, Estonia, Ethiopia, Fiji, Finland, France, Gabon, Gaza Strip, The Gambia, Georgia, Germany, Ghana, Greece, Grenada, Guatemala, Guinea, Guinea-Bissau, Guyana, Haiti, Holy Roman Empire, Honduras, Hungary, Iceland, India, Indonesia, Iran, Iraq, Republic of Ireland, Israel, Italy, Ivory Coast, Jamaica, Japan, Jonathanland, Jordan, Kazakhstan, Kenya, Kiribati, North Korea, South Korea, Kosovo, Kuwait, Kyrgyzstan, Laos, Latvia, Lebanon, Lesotho, Liberia, Libya, Liechtenstein, Lithuania, Luxembourg, Macedonia, Madagascar, Malawi, Malaysia, Maldives, Mali, Malta, Marshall Islands, Mauritania, Mauritius, Mexico, Micronesia, Moldova, Monaco, Mongolia, Montenegro, Morocco, Mount Athos, Mozambique, Namibia, Nauru, Nepal, Newfoundland, Netherlands, New Zealand, Nicaragua, Niger, Nigeria, Norway, Oman, Ottoman Empire, Pakistan, Palau, Panama, Papua New Guinea, Paraguay, Peru, Philippines, Poland, Portugal, Prussia, Qatar, Romania, Rome, Russian Federation, Rwanda, St Kitts & Nevis, St Lucia, Saint Vincent & the, Grenadines, Samoa, San Marino, Sao Tome & Principe, Saudi Arabia, Senegal, Serbia, Seychelles, Sierra Leone, Singapore, Slovakia, Slovenia, Solomon Islands, Somalia, South Africa, Spain, Sri Lanka, Sudan, Suriname, Swaziland, Sweden, Switzerland, Syria, Tajikistan, Tanzania, Thailand, Togo, Tonga, Trinidad & Tobago, Tunisia, Turkey, Turkmenistan, Tuvalu, Uganda, Ukraine, United Arab Emirates, United Kingdom, Uruguay, Uzbekistan, Vanuatu, Vatican City, Venezuela, Vietnam, Yemen, Zambia, Zimbabwe";
		String names = getContentFromFile("country_names.txt");
		String[] namesSplitted = names.split(",");
		List<String> countryNames = new ArrayList<String>();
		for (String name : namesSplitted) {
			countryNames.add(new String(name.trim()));
		}
		return countryNames;
	}

	private List<String> getMenuNames() {
		String names = getContentFromFile("menu_names.txt");
		String[] namesSplitted = names.split(",");
		List<String> menuNames = new ArrayList<String>();
		for (String name : namesSplitted) {
			menuNames.add(new String(name.trim()));
		}
		return menuNames;
	}

	private List<Integer> getRealMatchingItemIds() {
		String ids = getContentFromFile("real_matching_item_ids.txt");
		String[] idsSplitted = ids.split(",");
		List<Integer> idList = new ArrayList<Integer>();
		for (String name : idsSplitted) {
			idList.add(Integer.valueOf(name.trim()));
		}
		return idList;
	}
	
	private Map<Integer, String> getRealMatchingItemIdsAndNames() throws IOException {
		CSVParser csvParser = new CSVParser();
		StringBuilder errorMessage = new StringBuilder();
		matchingItemIdsAndNames = csvParser.parseMatchingItems("2015-01-07 Matching Items.txt", errorMessage);
		
		return matchingItemIdsAndNames;
	}

	public void generateMatchingItemIds(int numberOf2DimMatchingItems, int numberOf3DimMatchingItems) {

		String twoDimStringIds = "";
		for (int i = 0; i < numberOf2DimMatchingItems; i++) {
			twoDimStringIds += Integer.toString(TWO_DIMENSIONAL_BASE_NUMBER) + Integer.toString(i) + ", ";
		}
		writeFile(numberOf2DimMatchingItems + "_2dim_matching_item_ids.txt", twoDimStringIds);

		String threeDimStringIds = "";
		for (int j = 0; j < numberOf3DimMatchingItems; j++) {
			threeDimStringIds += Integer.toString(THREE_DIMENSIONAL_BASE_NUMBER) + Integer.toString(j) + ", ";
		}
		writeFile(numberOf3DimMatchingItems + "_3dim_matching_item_ids.txt", threeDimStringIds);

	}

	private String generateRandomDay() {
		int randomInt = rand.nextInt(30);
		randomInt++;
		String returnString = "";
		if (randomInt < 10)
			returnString += 0;
		returnString += randomInt;
		return returnString;
	}

	private List<Integer> getBaseProductIds() {
		Integer[] fPIds = new Integer[] { 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128,
				129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169,
				170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230,
				231, 232, 233, 234, 235, 236, 237, 300, 303, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 315, 316, 317, 318, 319, 320, 321, 400, 401, 402, 403, 404, 406, 409, 410, 411, 414, 415, 416, 418, 419,
				420, 421, 422, 423, 424, 425, 426, 427, 428, 429, 430, 431, 432, 434, 435, 437, 500, 501, 502, 503, 505, 512, 515, 516, 517, 518, 519, 600, 601, 602, 700, 701, 703, 704, 705, 706, 707, 708, 709, 710,
				711, 712, 713, 714, 800, 801, 802, 803, 804, 805, 806, 807, 808, 809, 810, 811, 812, 813, 814, 815, 816, 818, 819, 820, 821, 822, 823, 825, 826, 827, 828, 829, 830, 831, 832, 833, 834, 835, 836, 837,
				838, 839, 840, 841, 842, 843, 844, 845, 846, 847, 848, 851, 852, 900, 901, 902, 906, 1000, 1002, 1003, 1004, 1101, 1102, 1103, 1104, 1105, 1106, 1107, 1108, 1200, 1201, 1202, 1203, 1204, 1205, 1206,
				1207, 1208, 1209, 1210, 1213, 1214, 1215, 1216, 1218, 1219, 1220, 1221, 1224, 1225, 1226, 1228, 1229, 1231, 1233, 1234, 1236, 1238, 1239, 1240, 1241, 1242, 1243, 1244, 1246, 1249, 1250, 1251, 1252, 1253,
				1255, 1256, 1257, 1258, 1260, 1261, 1262, 1263, 1265, 1266, 1267, 1268, 1269, 1270, 1271, 1272, 1273, 1274, 1275, 1276, 1277, 1283, 1284, 1285, 1286, 1287, 1288, 1289, 1290, 1292, 1293, 1294, 1295, 1296,
				1297, 1298, 1299, 1301, 1306, 1307, 1308, 1309, 1310, 1311, 1313, 1315, 1316, 1321, 1322, 1332, 1334, 1335, 1339, 1341, 1343, 1502, 1503, 1504, 1505, 1506, 1521, 1522, 1532, 1541, 1546, 1547, 1554, 1555,
				1557, 1558, 1559, 1560, 1561, 1563, 1564, 1565, 1567, 1568, 1569, 1570, 1571, 1572, 1576, 1580, 1581, 1582, 1583, 1591, 1593, 1597, 1598, 1599, 1600, 1601, 1602, 1608, 1618, 1619, 1623, 1624, 1627, 1628,
				1629, 1630, 1631, 1632, 1633, 1634, 1635, 1636, 1637, 1638, 1639, 1640, 1641, 1642, 1643, 1644, 1645, 1646, 1647, 1648, 1649, 1650, 1651, 1652, 1653, 1654, 1655, 1656, 1657, 1658, 1659, 1660, 1661, 1662,
				1663, 1664, 1665, 1666, 1667, 1668, 1669, 1670, 1671, 1672, 1673, 1674, 1675, 1676 };

		return new ArrayList<Integer>(Arrays.asList(fPIds));
	}
	
	private static String[] getIngredientNames() {
		return new String[] { "Rapsöl", "Olivenöl", "Sonnenblumenöl", "Margarine", "Sojaöl", "Sesamöl", "Erdnussöl", "Distelöl", "Baumnussöl", "Palmöl", "Zitronensaft", "Tomaten",
			"Federkohl", "Kartoffeln", "Knollensellerie", "Schwarzwurzel", "Apfel", "Zwiebeln", "Knoblauch", "Spinat", "Maniok", "Mais", "Kichererbsen", "Kürbis", "Orangen", "Karotten", "Wirz", "Rosenkohl", "Lauch",
			"Bananen", "Stangensellerie", "Mangold", "Zucchini", "Aubergine", "Peperoni", "Broccoli", "Pastinaken", "Blumenkohl", "Linsen", "Kefen", "Oliven", "Erbsen", "Soja", "Bohnen", "Bleichspargel", "Birnen",
			"Avocado", "Peperoncini", "Rucola", "Kopfsalat", "Artischocken", "Batavia", "Chicorée", "Cherrytomaten", "Chinakohl", "Cicorino", "Eisbergsalat", "Endivie", "Fenchel", "Frühkartoffeln", "Gurken", "Kohlrabi",
			"Lattich", "Lollo", "Löwenzahn", "Nüsslisalat", "Portulak", "Radieschen", "Randen", "Rettich", "Romanesco", "Rotkohl", "Rüben", "Schalotte", "Topinambur", "Weisskohl", "Zuckerhut", "Grünspargel",
			"Pariserkarotten", "Rispentomaten", "Melone", "Petersilienwurzel", "Meerrettich", "Limette", "Trauben", "Rosinen", "Aprikosen", "Zitronen", "Zwetschgen", "Petersilie", "Salbei", "Salz", "Curry", "Pfeffer",
			"Koriander", "Galgant", "Schnittlauch", "Zucker", "Basilikum", "Ingwer", "Dillkraut", "Thymian", "Bärlauch", "Bohnenkraut", "Brennnesseln", "Dillsamen", "Eisenkraut", "Estragon", "Gänseblümchen",
			"Holunderblüten", "Kapuzinerblüte", "Kerbel", "Liebstöckel", "Majoran", "Minze", "Oregano", "Pelargonie", "Ringelblume", "Sauerampfer", "Veilchen", "Ysop", "Zitronenmelisse", "Waldmeister", "Chilli", "Zimt",
			"Safran", "Kokosnussmilch", "Soja Sauce", "Honig", "Essig", "Sambal Oelek", "Kokos", "Kapern", "Stärke", "Stärke", "Sojamilch", "Currypaste", "Senf", "Hefe", "Ketchup", "Aceto Balsamico", "Reisessig",
			"Backpulver", "Gelatine", "Himbeeressig", "Goldhirse", "Quinoa", "Spaghetti", "Weissmehl", "Hartweizengries", "Brot", "Maisgriess", "Reis", "Nudeln", "Gnocchi", "Couscous", "Bulghur", "Risottoreis", "Toast",
			"Grahammehl", "Halbweissmehl", "Roggenschrot", "Multikornmehl", "Blätterteig", "Rollgerste", "Grünkern", "Paniermehl", "Haferflocken", "Glasnudeln", "Hirseflocken", "Brötchen", "Dinkelmehl", "Reismehl",
			"Kuchenteig", "Vollkornmehl", "Quorn", "Tofu", "Tempeh", "Seitan", "Soja", "Pommes", "Gemüsebrühe", "Essiggurken", "Soja", "Strudelteig", "Sauerkraut", "Schokolade", "Schokolade", "Schokolade",
			"Cashewnüsse", "Waldpilze", "Erdnüsse", "Baumnüsse", "Champignons", "Pistazien", "Pinienkerne", "Kürbiskerne", "Mandeln", "Kerne", "Sonnenblumenkerne", "Sesam", "Haselnüsse", "Marroni", "Käse", "Eier",
			"Fisch", "Schweinefleisch", "Schinken", "Rindfleisch", "Kalbfleisch", "Hackfleisch", "Lammfleisch", "Poulet", "Hirschfleisch", "Rohwurst", "Wurst", "Kaninchenfleisch", "Kängurufleisch", "Straussenfleisch",
			"Kudu", "Bratwurst", "Bacon", "Quark", "Speck", "Ricotta", "Crevetten", "Ente", "Gorgonzola", "Mascarpone", "Feta", "Mozzarella", "Butter", "Milch", "Joghurt", "Rahm", "Mayonnaise", "Ziegenfleisch",
			"Garnelen", "Garnelenpaste", "Kalbsbratwurst", "Käse", "Felchen", "Brie", "Gruyere", "Edamer", "Kalbsgehacktes", "Emmentaler", "Parmesan", "Bündnerfleisch", "Lachsforelle", "Rahm", "Sauerrahm", "Konfitüre",
			"Marmelade", "Tomatenkonzentrat", "Milchpulver", "Gemüse", "Gewürze", "Nüsse", "Fleisch", "Wein", "Wasser", "Bier", "Fruchtsaft", "Orangensaft", "Apfelsaft", "Mineralwasser", "Buttermilch", "Algen",
			"Ananas", "Apfelpektin", "Artischockenherzen", "Brot", "Brötchen", "Brunnenkresse", "Cheddar", "Kartoffelchips", "Maischips", "Cornflakes", "Eichblattsalat", "Eigelb", "Eiweiss", "Erdbeeren", "Garnelen",
			"Himbeeren", "Joghurt", "Johannisbeeren", "Käse", "Käse", "Käse", "Kiwi", "Kompott", "Lachs", "Laugenbretzel", "Mango", "Mizuna", "Muskat", "Nektarine", "Pak Choi", "Passionsfrucht", "Pekannüsse",
			"Perlhuhn", "Pfirsich", "Red Bull", "Selleriesalz", "Senfblätter", "Sprossen", "Steinpilze", "Tabasco", "Teig", "Thaibasilikum", "Thunfisch", "Tomatensaft", "Trevisosalat", "Vacherin",
			"Violette Ritterlinge", "Waldbeeren", "Wolfsbarsch", "Ziegenkäse", "Zitronengras", "Kabeljau", "Languste", "Hummer", "Feigen", "Queller", "Kaffeebohnen", "Grapefruit", "Randensprossen", "Joghurt", "Joghurt",
			"Steckrüben", "Verveine", "Fischeier", "Randensamen", "Mangold", "Kapuzinerkresse", "Mesclun", "Moosbeere", "Goldbrasse", "Saibling", "Weizen", "Foie gras", "Taube", "Einsiedlerkrebs", "Pfifferlinge",
			"Trüffel", "Rosinenbrötchen", "Kakao", "Champagner", "Vermouth", "Portwein", "Pitaya", "Sternfrucht", "Gipfel", "Glucose", "Trimoline", "Cola", "Schnaps", "Gemüsebrühe", "Tomaten", "Tomatensugo", "Kompott",
			"Gipfel", "Rinderbrühe", "Kräutertee", "Mandarinen", "Pflaumen", "Limonade", "Limonade", "Grana Padano", "Pangasius", "Muscheln", "Tilsiter", "Nudeln", "Claressefilet", "Zanderfilet", "Roter Soldatenfisch",
			"Frühlingsrollen", "Zander", "Lorbeer", "Nelken", "Zitronenthymian", "Marsala", "Vollei", "Bergkräuter", "Silserbrot", "Eglifilet", "Spitzkohl", "Palmherzen", "Senfkörner", "Sternanis", "Eistee",
			"Schwarztee", "Grüntee", "Mineralwasser", "Kaffeerahm", "Rosmarin", "Salami", "Datteln", "Frühlingszwiebeln", "Mandarinensaft", "Mangopulpe", "Quitte", "Vanilleschoten", "Sauerkirschen", "Edamame",
			"Mu-Err-Pilze", "Hering", "Makrele", "Kalb", "Kalb", "Kalb", "Kalb", "Kalb", "Kalb", "Kalb", "Kalb", "Kalb", "Kalb", "Kalb", "Rind", "Rind", "Rind", "Rind", "Rind", "Rind", "Rind", "Rind", "Rind", "Rind",
			"Rind", "Rind", "Rind", "Butter", "Eier", "Käse", "Quark", "Rahm", "Speiseeis", "Milch", "Fett", "Hafer", "Hirse", "Mais", "Reis", "Maismehl", "Roggenmehl", "Sauermilch", "Milch", "Milch", "Ziegenmilch",
			"Milchpulver", "Molke", "Molkepulver", "Reisöl", "Stärke", "Stärke", "Valess" };
	}
}
