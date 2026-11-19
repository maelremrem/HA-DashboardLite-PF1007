package dev.maelab.haframe;

/** Pure layout engine shared by config validation, drawing and touch hit testing. */
final class TileGrid {
    static final int MAX_ROWS = 3;

    static final class Cell {
        final int column;
        final int row;
        final int columns;
        final int rows;

        Cell(int column, int row, int columns, int rows) {
            this.column = column;
            this.row = row;
            this.columns = columns;
            this.rows = rows;
        }
    }

    static final class Layout {
        final Cell[] cells;
        final int usedRows;

        Layout(Cell[] cells, int usedRows) {
            this.cells = cells;
            this.usedRows = Math.max(1, usedRows);
        }
    }

    static Layout place(int columns, int[] widths, int[] heights) {
        if (columns < 2 || columns > 4 || widths.length != heights.length ||
            widths.length > columns * MAX_ROWS) {
            throw new IllegalArgumentException("Invalid tile grid");
        }
        boolean[][] occupied = new boolean[MAX_ROWS][columns];
        Cell[] cells = new Cell[widths.length];
        int used = 0;
        for (int i = 0; i < widths.length; i++) {
            int width = widths[i], height = heights[i];
            if (width < 1 || width > columns || height < 1 || height > 2) {
                throw new IllegalArgumentException("Invalid tile span");
            }
            boolean placed = false;
            for (int row = 0; row + height <= MAX_ROWS && !placed; row++) {
                for (int col = 0; col + width <= columns; col++) {
                    boolean fits = true;
                    for (int r = row; r < row + height; r++) {
                        for (int c = col; c < col + width; c++) {
                            if (occupied[r][c]) fits = false;
                        }
                    }
                    if (!fits) continue;
                    for (int r = row; r < row + height; r++) {
                        for (int c = col; c < col + width; c++) {
                            occupied[r][c] = true;
                        }
                    }
                    cells[i] = new Cell(col, row, width, height);
                    used = Math.max(used, row + height);
                    placed = true;
                    break;
                }
            }
            if (!placed) throw new IllegalArgumentException("Tiles exceed the 3-row grid");
        }
        return new Layout(cells, used);
    }

    private TileGrid() {}
}
