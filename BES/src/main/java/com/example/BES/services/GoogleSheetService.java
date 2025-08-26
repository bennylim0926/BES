package com.example.BES.services;

import static org.mockito.Answers.values;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.config.GoogleSheetConfig;
import com.example.BES.dtos.GoogleSheetFileDto;
import com.example.BES.dtos.RegistrationDto;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
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
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;
import com.google.api.services.sheets.v4.model.ValueRange;

@Service
public class GoogleSheetService {
    // if any new category just add here and update the constructor to map the function
    // need to update dto as well
    private final static String KEYWORD = "Categories";
    private final static List<String> PAYMENT_KEYWORDS = new ArrayList<>(Arrays.asList("payment status", "email", "name","categories", "local/overseas"));

    private final static String POPPING = "Popping";
    private final static String WAACKING = "Waacking";
    private final static String LOCKING = "Locking";
    private final static String BREAKING = "Breaking";
    private final static String HIPHOP = "Hiphop";
    private final static String OPEN = "Open";
    private final static String AUDIENCE = "Audience";
    
    @Autowired
    GoogleSheetConfig config;

    Map<String, BiConsumer<GoogleSheetFileDto, List<String>>> actions;
    List<String> genres;

    public GoogleSheetService(){
        genres = new ArrayList<>(Arrays.asList(POPPING, WAACKING, LOCKING, BREAKING, HIPHOP, OPEN));
        actions = new HashMap<>();
        actions.put(POPPING, (dto,list) -> dto.setPopping(categoriesCount(list, POPPING)));
        actions.put(WAACKING, (dto,list) -> dto.setWaacking(categoriesCount(list, WAACKING)));
        actions.put(LOCKING, (dto,list) -> dto.setLocking(categoriesCount(list, LOCKING)));
        actions.put(BREAKING, (dto,list) -> dto.setBreaking(categoriesCount(list, BREAKING)));
        actions.put(HIPHOP, (dto,list) -> dto.setHiphop(categoriesCount(list, HIPHOP)));
        actions.put(OPEN, (dto,list) -> dto.setOpen(categoriesCount(list, OPEN)));
        actions.put(AUDIENCE, (dto,list) -> dto.setAudience(categoriesCount(list, AUDIENCE)));
    }
    
    // This is hardcoded we cannot confirmed where it will be located
    /* Correct order should be: 
     * 1. Search first row to identify the index of rows that has certain keyword eg. Category
     * 2. Store the indices in list, and by going through the list transform index to alphabet and in H:H format
     * 3. 
    */
    private List<Integer> getCategoriesColumns(String fileId) throws IOException{
        List<String> headers = readSheetHeader(fileId);
        List<Integer> matchingColumnIndices = new ArrayList<>();
        for(int i = 0; i < headers.size(); i++){
            if(headers.get(i).equalsIgnoreCase(KEYWORD)){
                // String colLetter = colIndexToLetter(i + 1);
                matchingColumnIndices.add(i);
            }
        }     
        return matchingColumnIndices;
    }
    
    public GoogleSheetFileDto getSheetInformationById(String fileId) throws IOException{
        GoogleSheetFileDto dto = new GoogleSheetFileDto();  
        List<String> matchingColumnAlphabet = new ArrayList<>();
        List<Integer> matchingCategoriesIndixes = getCategoriesColumns(fileId);
        for(Integer index : matchingCategoriesIndixes){
            String colLetter = colIndexToLetter(index + 1);
            matchingColumnAlphabet.add(colLetter + ":" + colLetter);
        }
                            
        // Get the value based on the columns identified
        // usually there will be only two which are local and overseas 
        BatchGetValuesResponse response = config
                                .getSheets()
                                .spreadsheets()
                                .values()
                                .batchGet(fileId)
                                .setRanges(matchingColumnAlphabet)
                                .execute();

        List<ValueRange> valueRanges = response.getValueRanges();

        List<List<Object>> localGenre = valueRanges.get(0).getValues();
        List<List<Object>> overseasGenre = valueRanges.get(1).getValues();
        List<String> combined = new ArrayList<>();
        for(int i = 1; i < localGenre.size(); i++){
            if(localGenre.get(i).size() == 0){
                combined.add(overseasGenre.get(i).get(0).toString());
            }else{
                combined.add(localGenre.get(i).get(0).toString());
            }
        }
        setDtoCategory(dto, combined);
        return dto;
    }

    public ValueRange updatePaymentStatus(String fileId) throws IOException{
        // Read thru the google sheets
        // information needed email, categories, donePayment, local/overseas
        // if no overseas should assume only local
        // if no payment by default should send email
        // need database to store the information eg. payment status, registered, audition number and etc
        // if donePayment column not there create one with all false
        Map<String, Integer> colIndexMap = new HashMap<>();
        List<String> headers = readSheetHeader(fileId);
        List<RegistrationDto> registeredParticipants = new ArrayList<>(); 
        ValueRange results = new ValueRange();
        
        // check if payment status header existed
        int rowSize = sheetsSize(fileId);
        if(!checkColumnExist(headers, "payment status")){
            insertPaymentCheckboxes(fileId, headers.size(), rowSize, getSheetId(fileId));
        }else{
            // Check with participants has paid
            // Then proceed to check if paid, then send qr
            
            // map the keywords with its columnIndex
            for(Integer i = 0; i< headers.size(); i ++){
                for(String keyword: PAYMENT_KEYWORDS){
                    if(headers.get(i).toLowerCase().contains(keyword.toLowerCase())){
                        colIndexMap.put(keyword, i);
                    }
                }
            }
            // Read everything except the headers
            String range = "A2:"+colIndexToLetter(headers.size());
            results = config.getSheets()
                            .spreadsheets()
                            .values()
                            .get(fileId, range)
                            .execute();
            
            List<List<String>> resultString = results.getValues().stream()
                                                    .map(row -> row.stream()
                                                        .map(Object::toString)   // convert each Object to String
                                                        .collect(Collectors.toList()))
                                                    .collect(Collectors.toList());
            
            for(List<String> res : resultString){
                RegistrationDto dto = new RegistrationDto();
                dto.setName(res.get(colIndexMap.get("name")).toString());
                dto.setEmail(res.get(colIndexMap.get("email")).toString());
                dto.setResidency(res.get(colIndexMap.get("local/overseas")).toString());
                dto.setPaymentStatus(Boolean.parseBoolean(res.get(colIndexMap.get("payment status"))));
                List<Integer> categoriesColumn = getCategoriesColumns(fileId);
                List<String> categories = new ArrayList<>();
                for(Integer i : categoriesColumn){
                    categories =  normalizeGenre(res.get(i).toString(),genres);
                    if(categories.size() != 0){
                        break;
                    }
                }
                dto.setCategories(categories);
                registeredParticipants.add(dto);
            }
            // for(RegistrationDto dto: registeredParticipants){
            //     System.out.println(dto.getName());
            //     System.out.println(dto.getEmail());
            //     System.out.println(dto.getResidency());
            //     System.out.println(dto.getPaymentStatus());
            //     System.out.println(dto.getCategories());
            //     System.out.println("\n");
            // }
        }
        if(!checkColumnExist(headers, "local")){
            System.out.println("This battle doesnt accept overseas battlers");
        }
        // check local/overseas
        // if(!headers.contains(""))
        return results;
    }

    // Get the count of the input category
    private Integer categoriesCount(List<String> data, String Category){
        return (int)data.stream()
                .filter(s -> s.toLowerCase().contains(Category.toLowerCase()))
                .count();
    }

    // This is to set the count of each genres
    private void setDtoCategory(GoogleSheetFileDto dto, List<String> data){
        Set<String> categories = new HashSet<String>(data);
        for (String category: categories){
            List<String> normalizeCategories = normalizeGenre(category, genres);
            for(String normalizeCategory: normalizeCategories){
                actions.get(normalizeCategory).accept(dto, data);
            }
        }
    }

    // Given a any string, return the genres that a string contains
    // eg. "Waacking 1v1, Open 1v1" -> ["Waacking", "Open"]
    private List<String> normalizeGenre(String event, List<String> genres){
        List<String> matchingGenre = new ArrayList<>();
        for(String genre : genres){
            if(event.contains(genre)){
                matchingGenre.add(genre);
            }
        }
        return matchingGenre;
    }

    // Map the index to Alphabet in Google Sheet eg. A == 1, B == 2
    private static String colIndexToLetter(int index) {
        StringBuilder result = new StringBuilder();
        while (index > 0) {
            int rem = (index - 1) % 26;
            result.insert(0, (char) ('A' + rem));
            index = (index - 1) / 26;
        }
        return result.toString();
    }

    // Return the first row of headers as a List
    private List<String> readSheetHeader(String fileId) throws IOException{
        ValueRange firstRow = config
                .getSheets()
                .spreadsheets()
                .values()
                .get(fileId, "1:1")
                .execute();
        List<String> headers = firstRow.getValues().get(0)
                .stream()
                .map(object -> Objects.toString(object, null))
                .toList();
        return headers;
    }

    private Boolean checkColumnExist(List<String> sheetsHeaders, String keyword){
        for(String header : sheetsHeaders){
            if(header.toLowerCase().contains(keyword.toLowerCase())){
                return true;
            }
        }
        return false;
    }

    private Request insertColumnRequest(int lastIndex, int sheetId) {
        return new Request()
                .setInsertDimension(new InsertDimensionRequest()
                .setRange(new DimensionRange()
                    .setSheetId(sheetId)
                    .setDimension("COLUMNS")
                    .setStartIndex(lastIndex)
                    .setEndIndex(lastIndex + 1))
                .setInheritFromBefore(true));
    } 

    private Request setHeaderRequest(int lastIndex, int sheetId){
        CellData headerCell = new CellData()
                                .setUserEnteredValue(new ExtendedValue().setStringValue("Payment Status"));

        RowData rowData = new RowData().setValues(Collections.singletonList(headerCell));
        return new Request()
            .setUpdateCells(new UpdateCellsRequest()
                .setRange(new GridRange()
                    .setSheetId(sheetId)
                    .setStartRowIndex(0)
                    .setEndRowIndex(1)
                    .setStartColumnIndex(lastIndex )
                    .setEndColumnIndex(lastIndex + 1))
                .setRows(Collections.singletonList(rowData))
                .setFields("userEnteredValue"));
        }

    private DataValidationRule setCheckBoxValidation(){
        BooleanCondition condition = new BooleanCondition()
        .setType("BOOLEAN");
        return new DataValidationRule()
                    .setCondition(condition)
                    .setStrict(true)
                    .setShowCustomUi(true);
    }

    private Request checkBoxRequest(int lastIndex, int lastRowIndex, int sheetId){
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

    private void insertPaymentCheckboxes(String sheetsId, int headerLastIndex, int lastRowIndex, int sheetId) throws IOException{
        BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
                                        .setRequests(Arrays.asList(
                                            insertColumnRequest(headerLastIndex, sheetId),
                                            setHeaderRequest(headerLastIndex, sheetId),
                                            checkBoxRequest(headerLastIndex, lastRowIndex, sheetId)
                                        ));
        config.getSheets().spreadsheets().batchUpdate(sheetsId, body).execute();
    }

    private int sheetsSize(String fileId) throws IOException{
        ValueRange response = config.getSheets().spreadsheets().values()
                                        .get(fileId, "A:A") // read column A
                                        .execute();

        List<List<Object>> values = response.getValues();
        int rowCount = (values != null) ? values.size() : 0;
        return Math.max(0, rowCount);
    }

    private int getSheetId(String fileId) throws IOException{
        Spreadsheet sheet = config.getSheets().spreadsheets().get(fileId).execute();
        List<Sheet> sheets = sheet.getSheets();
        return sheets.get(0).getProperties().getSheetId();
    }
}