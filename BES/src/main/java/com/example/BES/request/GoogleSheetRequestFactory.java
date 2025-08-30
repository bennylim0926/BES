package com.example.BES.request;

import java.util.Collections;

import com.google.api.services.sheets.v4.model.BooleanCondition;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.DataValidationRule;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.InsertDimensionRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.SetDataValidationRequest;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;

public class GoogleSheetRequestFactory {
    public static Request insertColumn(int lastIndex, int sheetId) {
        return new Request()
            .setInsertDimension(new InsertDimensionRequest()
                .setRange(new DimensionRange()
                    .setSheetId(sheetId)
                    .setDimension("COLUMNS")
                    .setStartIndex(lastIndex)
                    .setEndIndex(lastIndex + 1))
                .setInheritFromBefore(true));
    }

    public static Request headerCell(int colIndex, int sheetId, String headerName) {
        CellData cell = new CellData()
            .setUserEnteredValue(new ExtendedValue().setStringValue(headerName));

        RowData rowData = new RowData().setValues(Collections.singletonList(cell));
        return new Request().setUpdateCells(new UpdateCellsRequest()
            .setRange(new GridRange()
                .setSheetId(sheetId)
                .setStartRowIndex(0)
                .setEndRowIndex(1)
                .setStartColumnIndex(colIndex)
                .setEndColumnIndex(colIndex + 1))
            .setRows(Collections.singletonList(rowData))
            .setFields("userEnteredValue"));
    }

    public static Request checkBoxRequest(int lastIndex, int lastRowIndex, int sheetId){
        return new Request()
            .setSetDataValidation(new SetDataValidationRequest()
                .setRange(new GridRange()
                    .setSheetId(sheetId)
                    .setStartRowIndex(1)  // start below header
                    .setEndRowIndex(lastRowIndex) // adjust to your row count
                    .setStartColumnIndex(lastIndex)
                    .setEndColumnIndex(lastIndex + 1))
                .setRule(setCheckBoxValidation()));
    }
    
    private static DataValidationRule setCheckBoxValidation(){
        BooleanCondition condition = new BooleanCondition()
        .setType("BOOLEAN");
        return new DataValidationRule()
                    .setCondition(condition)
                    .setStrict(true)
                    .setShowCustomUi(true);
    }
}
