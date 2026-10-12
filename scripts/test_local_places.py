import importlib.util
from pathlib import Path
import unittest


class LocalPlaceBuildTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("builder", Path(__file__).with_name("build_local_places.py"))
        cls.builder = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.builder)

    def row(self, ident, name, aliases, feature, parent="15", district="6203"):
        return [str(ident), name, name, aliases, "38.5", "102.2", "A", feature, "CN", "", parent, district, str(ident) if feature == "ADM3" else "", "", "0", "", "", "Asia/Shanghai", "2026-10-10"]

    def test_chinese_names_and_true_parent_codes_survive(self):
        rows = [
            self.row(1, "Gansu", "甘肃,甘肃省", "ADM1", district=""),
            self.row(2, "Jinchang Shi", "金昌,金昌市", "ADM2"),
            self.row(3, "Jinchuan Qu", "金川,金川区,jin chuan qu", "ADM3"),
        ]
        places, _ = self.builder.build_records({"CN": rows})
        county = next(p for p in places if p["id"] == "CN:3")
        self.assertEqual("金川区", county["name"])
        self.assertEqual("甘肃省·金昌市", county["affiliation"])

    def test_unknown_parent_is_not_filled_by_nearest_region(self):
        rows = [self.row(3, "Jinchuan Qu", "金川区", "ADM3", parent="missing")]
        places, _ = self.builder.build_records({"CN": rows})
        self.assertEqual("", places[0]["affiliation"])

    def test_mixed_japanese_and_descriptive_aliases_are_not_chinese_display_names(self):
        row = self.row(1, "Inner Mongolia", "内モンゴル自治区,内蒙古自治区", "ADM1")
        self.assertEqual("内蒙古自治区", self.builder.display_name(row))
        row = self.row(2, "Taiwan", "臺灣省 or 台灣省,台湾省", "ADM1")
        self.assertEqual("台湾省", self.builder.display_name(row))

    def test_invalid_coordinates_and_duplicate_ids_reject_the_build(self):
        row = self.row(1, "Test", "测试区", "ADM3")
        with self.assertRaises(ValueError):
            self.builder.build_records({"CN": [row, row]})
        row[4] = "999"
        with self.assertRaises(ValueError):
            self.builder.build_records({"CN": [row]})

    def test_historic_and_township_records_do_not_sneak_into_county_scope(self):
        rows = [self.row(1, "Old", "旧县", "ADM3H"), self.row(2, "Town", "测试乡", "ADM4")]
        places, _ = self.builder.build_records({"CN": rows})
        self.assertEqual([], places)


if __name__ == "__main__":
    unittest.main()
