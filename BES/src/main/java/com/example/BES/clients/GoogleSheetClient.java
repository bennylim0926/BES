package com.example.BES.clients;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.config.GoogleSheetConfig;
import com.example.BES.request.GoogleSheetRequestFactory;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;

@Service
public class GoogleSheetClient {
    @Autowired
    private GoogleSheetConfig config;

    public ValueRange getRange(String fileId, String range) throws IOException {
        return config.getSheets()
                .spreadsheets()
                .values()
                .get(fileId, range)
                .execute();
    }

    public BatchGetValuesResponse batchGet(String fileId, List<String> ranges) throws IOException {
        return config.getSheets()
                .spreadsheets()
                .values()
                .batchGet(fileId)
                .setRanges(ranges)
                .execute();
    }

    public void batchUpdate(String fileId, BatchUpdateSpreadsheetRequest body) throws IOException {
        config.getSheets()
              .spreadsheets()
              .batchUpdate(fileId, body)
              .execute();
    }

    public Spreadsheet getSpreadsheet(String fileId) throws IOException {
        return config.getSheets().spreadsheets().get(fileId).execute();
    }

    public int getSheetSize(String fileId) throws IOException{
        ValueRange response = config.getSheets().spreadsheets().values()
                                        .get(fileId, "A:A")
                                        .execute();

        List<List<Object>> values = response.getValues();
        int rowCount = (values != null) ? values.size() : 0;
        return Math.max(0, rowCount);
    }

    public int getSheetId(String fileId) throws IOException{
        return getSpreadsheet(fileId).getSheets().get(0).getProperties().getSheetId();
    }

    public void insertPaymentCheckboxes(String sheetsId, int headerLastIndex, int lastRowIndex, int sheetId) throws IOException{
        BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
                                        .setRequests(Arrays.asList(
                                            GoogleSheetRequestFactory.insertColumn(headerLastIndex, sheetId),
                                            GoogleSheetRequestFactory.headerCell(headerLastIndex, sheetId, "Payment Status"),
                                            GoogleSheetRequestFactory.checkBoxRequest(headerLastIndex, lastRowIndex, sheetId)
                                        ));
        batchUpdate(sheetsId, body);
    }
}
