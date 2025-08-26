package com.example.BES.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.clients.GoogleSheetClient;
import com.example.BES.config.GoogleSheetConfig;
import com.example.BES.dtos.GoogleSheetFileDto;
import com.example.BES.dtos.RegistrationDto;
import com.example.BES.enums.Genre;
import com.example.BES.mapper.RegistrationDtoMapper;
import com.example.BES.parsers.GoogleSheetParser;
import com.example.BES.request.GoogleSheetRequestFactory;
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
    private static final String CATEGORY_KEYWORD = "Categories";

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
    private GoogleSheetClient client;

    @Autowired
    private RegistrationDtoMapper mapper;

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

    /* Correct order should be: 
     * 1. Search first row to identify the index of rows that has certain keyword eg. Category
     * 2. Store the indices in list, and by going through the list transform index to alphabet and in H:H format
     * 3. 
    */
    private List<Integer> getCategoriesColumns(String fileId) throws IOException{
        ValueRange headerRange = client.getRange(fileId, "1:1");
        List<String> headers = GoogleSheetParser.readHeaders(headerRange);
        // List<String> headers = readSheetHeader(fileId);
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

        ValueRange headerRange = client.getRange(fileId, "1:1");
        List<String> headers = GoogleSheetParser.readHeaders(headerRange);
        // List<String> headers = readSheetHeader(fileId);
        List<RegistrationDto> registeredParticipants = new ArrayList<>(); 
        ValueRange results = new ValueRange();
        
        // check if payment status header existed
        int rowSize = client.getSheetSize(fileId);
        if(!GoogleSheetParser.columnExists(headers, "payment status")){
            insertPaymentCheckboxes(fileId, headers.size(), rowSize, client.getSheetId(fileId));
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
                    categories =  GoogleSheetParser.normalizeGenre(res.get(i).toString(),genres);
                    if(categories.size() != 0){
                        break;
                    }
                }
                dto.setCategories(categories);
                registeredParticipants.add(dto);
            }
        }
        if(!GoogleSheetParser.columnExists(headers, "local")){
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
            // List<String> normalizeCategories = normalizeGenre(category, genres);
            List<String> normalizeCategories = GoogleSheetParser.normalizeGenre(category, genres);
            for(String normalizeCategory: normalizeCategories){
                actions.get(normalizeCategory).accept(dto, data);
            }
        }
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

    private void insertPaymentCheckboxes(String sheetsId, int headerLastIndex, int lastRowIndex, int sheetId) throws IOException{
        BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
                                        .setRequests(Arrays.asList(
                                            GoogleSheetRequestFactory.insertColumn(headerLastIndex, sheetId),
                                            GoogleSheetRequestFactory.headerCell(headerLastIndex, sheetId, "Payment Status"),
                                            GoogleSheetRequestFactory.checkBoxRequest(headerLastIndex, lastRowIndex, sheetId)
                                        ));
        config.getSheets().spreadsheets().batchUpdate(sheetsId, body).execute();
    }
}