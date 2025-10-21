package com.example.BES.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.dtos.AddParticipantToEventDto;
import com.example.BES.dtos.GoogleSheetFileDto;
import com.example.BES.enums.Genre;
import com.example.BES.enums.SheetHeader;
import com.example.BES.mapper.RegistrationDtoMapper;
import com.example.BES.parsers.GoogleSheetParser;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.zxing.WriterException;

import jakarta.mail.MessagingException;

@Service
public class GoogleSheetService {
    // if any new category just add here and update the constructor to map the function
    // need to update dto as well
    private static final String CATEGORY_KEYWORD = "categor";
    private final static List<String> PAYMENT_KEYWORDS = new ArrayList<>(Arrays.asList(SheetHeader.EMAIL,SheetHeader.NAME,SheetHeader.PAYMENT_STATUS,SheetHeader.CATEGORIES,SheetHeader.LOCAL_OVERSEAS));    
    
    @Autowired
    private GoogleSheetClient sheetClient;

    @Autowired
    private RegistrationDtoMapper mapper;

    @Autowired
    GoogleSheetConfig config;

    Map<String, BiConsumer<GoogleSheetFileDto, List<String>>> actions;
    List<String> genres;

    public GoogleSheetService(){
        genres = new ArrayList<>(Arrays.asList(Genre.POPPING.getLabel(), Genre.WAACKING.getLabel(), 
                                                Genre.LOCKING.getLabel(), Genre.BREAKING.getLabel(), 
                                                Genre.HIPHOP.getLabel(), Genre.OPEN.getLabel(),
                                                Genre.AUDIENCE.getLabel(), Genre.ROOKIE.getLabel(),
                                                Genre.SMOKE.getLabel()));
        actions = new HashMap<>();
        actions.put(Genre.POPPING.getLabel(), (dto,list) -> dto.setPopping(categoriesCount(list, Genre.POPPING.getLabel())));
        actions.put(Genre.WAACKING.getLabel(), (dto,list) -> dto.setWaacking(categoriesCount(list, Genre.WAACKING.getLabel())));
        actions.put(Genre.LOCKING.getLabel(), (dto,list) -> dto.setLocking(categoriesCount(list, Genre.LOCKING.getLabel())));
        actions.put(Genre.BREAKING.getLabel(), (dto,list) -> dto.setBreaking(categoriesCount(list, Genre.BREAKING.getLabel())));
        actions.put(Genre.HIPHOP.getLabel(), (dto,list) -> dto.setHiphop(categoriesCount(list, Genre.HIPHOP.getLabel())));
        actions.put(Genre.OPEN.getLabel(), (dto,list) -> dto.setOpen(categoriesCount(list, Genre.OPEN.getLabel())));
        actions.put(Genre.AUDIENCE.getLabel(), (dto,list) -> dto.setAudience(categoriesCount(list, Genre.AUDIENCE.getLabel())));
        actions.put(Genre.ROOKIE.getLabel(), (dto,list) -> dto.setRookie(categoriesCount(list, Genre.ROOKIE.getLabel())));
        actions.put(Genre.SMOKE.getLabel(), (dto,list) -> dto.setSmoke(categoriesCount(list, Genre.SMOKE.getLabel())));
    }

    /* Correct order should be: 
     * 1. Search first row to identify the index of rows that has certain keyword eg. Category
     * 2. Store the indices in list, and by going through the list transform index to alphabet and in H:H format
     * 3. 
    */
    
    // get numbers of participants of each genre
    public GoogleSheetFileDto getParticipantsBreakDown(String fileId) throws IOException{
        GoogleSheetFileDto dto = new GoogleSheetFileDto();  
        List<String> matchingColumnAlphabet = new ArrayList<>();
        List<Integer> matchingCategoriesIndixes = getCategoriesColumns(fileId);
        for(Integer index : matchingCategoriesIndixes){
            String colLetter = colIndexToLetter(index + 1);
            matchingColumnAlphabet.add(colLetter + ":" + colLetter);
        }
                            
        // Get the value based on the columns identified
        // usually there will be only two which are local and overseas 
        BatchGetValuesResponse response = sheetClient.batchGet(fileId, matchingColumnAlphabet);
        List<ValueRange> valueRanges = response.getValueRanges();
        List<List<Object>> localGenre = valueRanges.get(0).getValues();
        List<List<Object>> overseasGenre = new ArrayList<>();
        if(valueRanges.size()>1){
            overseasGenre = valueRanges.get(1).getValues();
        }
        
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

    // When there is a new form, need to insert payment column for payment validation
    public void insertPaymentColumn(String fileId) throws IOException{
        ValueRange headerRange = sheetClient.getRange(fileId, "1:1");
        List<String> headers = GoogleSheetParser.readHeaders(headerRange);
        if(!GoogleSheetParser.columnExists(headers, SheetHeader.PAYMENT_STATUS)){
            int rowSize = sheetClient.getSheetSize(fileId);
            sheetClient.insertPaymentCheckboxes(fileId, headers.size(), rowSize, sheetClient.getSheetId(fileId));
        }
    }

    public List<AddParticipantDto> getAllPaidParticipants(AddParticipantToEventDto dto)
        throws IOException, MessagingException, WriterException{
        List<AddParticipantDto> paidRegisteredParticipants = new ArrayList<>();    
        Map<String, Integer> colIndexMap = getColumnIndexMap(dto.fileId);
        List<List<String>> resultString = getsheetAllRows(dto.fileId);
        List<Integer> categoriesColumn = getCategoriesColumns(dto.fileId);                   
        for(List<String> res : resultString){
            AddParticipantDto participants = mapper.mapRow(res, colIndexMap, categoriesColumn, genres);
            if(participants.getPaymentStatus()){
                paidRegisteredParticipants.add(participants);
            }
        }                   
        return paidRegisteredParticipants;
    }

    public Integer getSheetSizeService(String fileId) throws IOException{
        return sheetClient.getSheetSize(fileId) - 1;
    }

    /*
     * Helper functions region
     */
    private Map<String,Integer> getColumnIndexMap(String fileId) throws IOException{
        // email has no problem
        // categories are handled differently
        // local/overseas should be handled more nicely
        // name may have multiple names for team
        Map<String, Integer> colIndexMap = new HashMap<>();
        List<String> headers = getHeaders(fileId);
        // check how many contain name, if more than one, take team name
        Integer nameCount = getNameHeaderCount(headers);
        if(nameCount > 1){
            headers = removeExtraName(headers);
        }
        for(Integer i = 0; i< headers.size(); i ++){
            for(String keyword: PAYMENT_KEYWORDS){
                if(headers.get(i).toLowerCase().contains(keyword.toLowerCase())){
                    colIndexMap.put(keyword, i);
                }
            }
        }
        return colIndexMap;
    }

    private Integer getNameHeaderCount(List<String> headers){
        Integer count = 0;
        for(String h : headers){
            if(h.toLowerCase().contains("name")){
                count += 1;
            }
        }
        return count;
    }

    // This is because Team battle form will consist of multiple name columns
    // We only need to keep Team name
    private List<String> removeExtraName(List<String> headers) {
        List<String> mutableHeaders = new ArrayList<>(headers);
    
        // Step 1: check if "team name" exists
        boolean hasTeamName = headers.stream()
            .anyMatch(h -> h.toLowerCase().contains("team name"));
    
        boolean foundFirstName = false;
    
        // Step 2: iterate and mark extra "name" columns as "ignore"
        for (int i = 0; i < mutableHeaders.size(); i++) {
            String value = mutableHeaders.get(i).toLowerCase();
    
            if (value.contains("name")) {
                if (hasTeamName) {
                    // Case A: "team name" exists → ignore all other "name" columns
                    if (!value.contains("team name")) {
                        mutableHeaders.set(i, "ignore");
                    }
                } else {
                    // Case B: no "team name" → keep first "name" only
                    if (!foundFirstName) {
                        foundFirstName = true; // keep this one
                    } else {
                        mutableHeaders.set(i, "ignore"); // ignore subsequent ones
                    }
                }
            }
        }
        return mutableHeaders;
    }

    private List<String> getHeaders(String fileId) throws IOException{
        ValueRange headerRange = sheetClient.getRange(fileId, "1:1");
        return GoogleSheetParser.readHeaders(headerRange);

    }

    private List<List<String>> getsheetAllRows(String fileId) throws IOException{
        List<String> headers = getHeaders(fileId);
        String range = "A2:"+colIndexToLetter(headers.size());
        ValueRange results = new ValueRange();
        results = sheetClient.getRange(fileId, range);
        return results.getValues().stream()
                        .map(row -> row.stream()
                            .map(Object::toString)   // convert each Object to String
                            .collect(Collectors.toList()))
                        .collect(Collectors.toList());       
    }

    // Get the count of the input category
    private Integer categoriesCount(List<String> data, String Category){
        return (int)data.stream()
                .filter(s -> s.toLowerCase().contains(Category.toLowerCase()))
                .count();
    }

    // identify columns "Categories" from the sheet
    private List<Integer> getCategoriesColumns(String fileId) throws IOException{
        ValueRange headerRange = sheetClient.getRange(fileId, "1:1");
        List<String> headers = GoogleSheetParser.readHeaders(headerRange);
        List<Integer> matchingColumnIndices = new ArrayList<>();
        for(int i = 0; i < headers.size(); i++){
            if(headers.get(i).toLowerCase().contains(CATEGORY_KEYWORD.toLowerCase())){
                matchingColumnIndices.add(i);
            }
        }     
        return matchingColumnIndices;
    }

    // This is to set the count of each genres
    private void setDtoCategory(GoogleSheetFileDto dto, List<String> data){
        Set<String> categories = new HashSet<String>(data);
        for (String category: categories){
            List<String> normalizeCategories = GoogleSheetParser.normalizeGenre(category.toLowerCase(), genres);
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
}