"""Tests of tools/build_tunnel_asset.py (standard library only): python3 tools/test_build_tunnel_asset.py"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_tunnel_asset as b  # noqa: E402


class TunnelAssetTest(unittest.TestCase):
    def test_tunnel_tags(self):
        self.assertTrue(b.is_tunnel({"tunnel": "yes"}))
        self.assertTrue(b.is_tunnel({"location": "underground"}))
        self.assertTrue(b.is_tunnel({"layer": "-1"}))
        self.assertFalse(b.is_tunnel({"layer": "-1", "cutting": "yes"}))
        self.assertFalse(b.is_tunnel({"tunnel": "no"}))
        self.assertFalse(b.is_tunnel({"bridge": "yes", "layer": "1"}))

    def test_main_lines_only(self):
        self.assertTrue(b.is_main_line({"railway": "subway"}))
        self.assertFalse(b.is_main_line({"railway": "subway", "service": "yard"}))
        self.assertFalse(b.is_main_line({"railway": "rail", "usage": "industrial"}))
        self.assertFalse(b.is_main_line({"railway": "tram"}))

    def test_encode_round_trip(self):
        cells = {5, 41, 1000, 1001}
        text = b.encode(cells)
        decoded, total = [], 0
        for part in text.split(","):
            total += int(part, 36)
            decoded.append(total)
        self.assertEqual(sorted(cells), decoded)

    def test_cells_along_a_way(self):
        lat = b.BOX[0] + 0.1
        cells = b.cells_along([(lat, b.BOX[1] + 0.1), (lat, b.BOX[1] + 0.1 + 3 * b.CELL_LON)])
        self.assertEqual(4, len(cells))
        self.assertEqual(set(), b.cells_along([(10.0, 10.0), (10.1, 10.1)]))


    def test_germany_grid_fits_an_int(self):
        self.assertLess(b.rows() * b.cols(), 2 ** 31 - 1)
        # A place in the south west and one in the north east of Germany are in the box.
        self.assertIsNotNone(b.cell_of(47.6, 7.6))
        self.assertIsNotNone(b.cell_of(54.6, 14.2))

    def test_open_cells_only_near_a_tunnel(self):
        lat, lon = 48.505, 9.505  # a made-up city tunnel far from Berlin
        tunnel_way = [(lat, lon), (lat, lon + 10 * b.CELL_LON)]
        open_way = [(lat, lon + 10 * b.CELL_LON), (lat, lon + 40 * b.CELL_LON)]
        far_way = [(lat + 0.5, lon), (lat + 0.5, lon + 20 * b.CELL_LON)]
        tunnel, open_ = b.build([(tunnel_way, True), (open_way, False), (far_way, False)])
        self.assertEqual(11, len(tunnel))
        # The portal cell is in both lists, then two more open cells: nothing further away.
        portal = b.cell_of(lat, lon + 10 * b.CELL_LON)
        self.assertEqual(portal, max(tunnel))
        self.assertEqual({portal, portal + 1, portal + 2}, open_)

    def test_cell_size(self):
        saved = (b.CELL_LAT, b.CELL_LON)
        try:
            b.set_cell_meters(75)
            self.assertAlmostEqual(75 / 111_320, b.CELL_LAT, places=5)
            self.assertGreater(b.CELL_LON, b.CELL_LAT)
        finally:
            b.CELL_LAT, b.CELL_LON = saved

if __name__ == "__main__":
    unittest.main()
