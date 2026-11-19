package dev.maelab.haframe;

public final class TileGridTest {
    private static void check(boolean ok) {
        if (!ok) throw new AssertionError("Invalid layout");
    }

    private static void invalid(int cols, int[] w, int[] h) {
        try {
            TileGrid.place(cols, w, h);
            throw new AssertionError("Expected invalid layout");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    public static void main(String[] args) {
        TileGrid.Layout basic = TileGrid.place(2,
            new int[]{1, 1, 1, 1, 1, 1},
            new int[]{1, 1, 1, 1, 1, 1});
        check(basic.usedRows == 3);
        check(basic.cells[2].column == 0 && basic.cells[2].row == 1);

        TileGrid.Layout spans = TileGrid.place(3,
            new int[]{2, 1, 1, 2, 1},
            new int[]{2, 1, 1, 1, 1});
        check(spans.usedRows == 3);
        check(spans.cells[0].columns == 2 && spans.cells[0].rows == 2);
        check(spans.cells[1].column == 2 && spans.cells[1].row == 0);
        check(spans.cells[3].row == 2);

        invalid(2, new int[]{2, 2, 2, 2}, new int[]{1, 1, 1, 1});
        invalid(3, new int[]{4}, new int[]{1});
        invalid(3, new int[]{1}, new int[]{3});
        invalid(1, new int[]{1}, new int[]{1});
        System.out.println("Tile grid tests passed");
    }
}
