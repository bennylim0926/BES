package com.example.BES.services;

import java.io.IOException;
import java.io.OutputStream;
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
import org.springframework.boot.autoconfigure.couchbase.CouchbaseProperties.Io;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Service;

import com.example.BES.clients.GoogleSheetClient;
import com.example.BES.config.GoogleSheetConfig;
import com.example.BES.dtos.GoogleSheetFileDto;
import com.example.BES.dtos.ParticpantsDto;
import com.example.BES.enums.EmailTemplates;
import com.example.BES.enums.Genre;
import com.example.BES.enums.SheetHeader;
import com.example.BES.mapper.RegistrationDtoMapper;
import com.example.BES.parsers.GoogleSheetParser;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.util.ByteArrayDataSource;

@Service
public class GoogleSheetService {
    // if any new category just add here and update the constructor to map the function
    // need to update dto as well
    private static final String CATEGORY_KEYWORD = "Categories";
    private final static List<String> PAYMENT_KEYWORDS = new ArrayList<>(Arrays.asList(SheetHeader.EMAIL,SheetHeader.NAME,SheetHeader.PAYMENT_STATUS,SheetHeader.CATEGORIES,SheetHeader.LOCAL_OVERSEAS));    
    
    @Autowired
    private GoogleSheetClient sheetClient;

    @Autowired
    private RegistrationDtoMapper mapper;

    @Autowired
    GoogleSheetConfig config;

    @Autowired
    MailSenderService mailService;



    Map<String, BiConsumer<GoogleSheetFileDto, List<String>>> actions;
    List<String> genres;

    public GoogleSheetService(){
        genres = new ArrayList<>(Arrays.asList(Genre.POPPING.getLabel(), Genre.WAACKING.getLabel(), 
                                                Genre.LOCKING.getLabel(), Genre.BREAKING.getLabel(), 
                                                Genre.HIPHOP.getLabel(), Genre.OPEN.getLabel()));
        actions = new HashMap<>();
        actions.put(Genre.POPPING.getLabel(), (dto,list) -> dto.setPopping(categoriesCount(list, Genre.POPPING.getLabel())));
        actions.put(Genre.WAACKING.getLabel(), (dto,list) -> dto.setWaacking(categoriesCount(list, Genre.WAACKING.getLabel())));
        actions.put(Genre.LOCKING.getLabel(), (dto,list) -> dto.setLocking(categoriesCount(list, Genre.LOCKING.getLabel())));
        actions.put(Genre.BREAKING.getLabel(), (dto,list) -> dto.setBreaking(categoriesCount(list, Genre.BREAKING.getLabel())));
        actions.put(Genre.HIPHOP.getLabel(), (dto,list) -> dto.setHiphop(categoriesCount(list, Genre.HIPHOP.getLabel())));
        actions.put(Genre.OPEN.getLabel(), (dto,list) -> dto.setOpen(categoriesCount(list, Genre.OPEN.getLabel())));
        actions.put(Genre.AUDIENCE.getLabel(), (dto,list) -> dto.setAudience(categoriesCount(list, Genre.AUDIENCE.getLabel())));
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

    // When there is a new form, need to insert payment column for payment validation
    public void insertPaymentColumn(String sheetId) throws IOException{
        ValueRange headerRange = sheetClient.getRange(sheetId, "1:1");
        List<String> headers = GoogleSheetParser.readHeaders(headerRange);
        if(!GoogleSheetParser.columnExists(headers, SheetHeader.PAYMENT_STATUS)){
            int rowSize = sheetClient.getSheetSize(sheetId);
            sheetClient.insertPaymentCheckboxes(sheetId, headers.size(), rowSize, sheetClient.getSheetId(sheetId));
        }
    }

    // get all "ticked" participants
    // BUT this is not enuf, need to confirm if haven receive email as well
    public List<ParticpantsDto> getAllNewPaidParticipants(String fileId) throws IOException{
        List<ParticpantsDto> paidRegisteredParticipants = new ArrayList<>();    
        Map<String, Integer> colIndexMap = getColumnIndexMap(fileId);
        List<List<String>> resultString = getsheetAllRows(fileId);
        List<Integer> categoriesColumn = getCategoriesColumns(fileId);  
        System.out.println(resultString);                          
        for(List<String> res : resultString){
            ParticpantsDto participants = mapper.mapRow(res, colIndexMap, categoriesColumn, genres);
            if(participants.getPaymentStatus()){
                // need to access to database to check if sent isnt marked true
                paidRegisteredParticipants.add(mapper.mapRow(res, colIndexMap, categoriesColumn, genres));
            }
        }
        return paidRegisteredParticipants;
    }

    public void updatePaymentStatus(String fileId, String eventName) throws IOException, MessagingException{
        // Read thru the google sheets
        // information needed email, categories, donePayment, local/overseas
        // if no overseas should assume only local
        // if no payment by default should send email
        // need database to store the information eg. payment status, registered, audition number and etc
        // if donePayment column not there create one with all false
        ValueRange headerRange = sheetClient.getRange(fileId, "1:1");
        Map<String, Integer> colIndexMap = new HashMap<>();
        List<String> headers = GoogleSheetParser.readHeaders(headerRange);
        List<ParticpantsDto> registeredParticipants = new ArrayList<>(); 
        ValueRange results = new ValueRange();
        
        // check if payment status header existed
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
        List<Integer> categoriesColumn = getCategoriesColumns(fileId);
        results= sheetClient.getRange(fileId, range);
        List<List<String>> resultString = results.getValues().stream()
                                                .map(row -> row.stream()
                                                    .map(Object::toString)   // convert each Object to String
                                                    .collect(Collectors.toList()))
                                                .collect(Collectors.toList());                                
        for(List<String> res : resultString){
            mapper.mapRow(res, colIndexMap, categoriesColumn, genres);
            registeredParticipants.add(mapper.mapRow(res, colIndexMap, categoriesColumn, genres));
        }
        
        if(!GoogleSheetParser.columnExists(headers, SheetHeader.LOCAL_OVERSEAS)){
            System.out.println("This battle doesnt accept overseas battlers");
        }
        // check local/overseas
        // if(!headers.contains(""))
        // return results;
    }

    /*
     * Helper functions region
     */
    private Map<String,Integer> getColumnIndexMap(String fileId) throws IOException{
        Map<String, Integer> colIndexMap = new HashMap<>();
        List<String> headers = getHeaders(fileId);
        for(Integer i = 0; i< headers.size(); i ++){
            for(String keyword: PAYMENT_KEYWORDS){
                if(headers.get(i).toLowerCase().contains(keyword.toLowerCase())){
                    colIndexMap.put(keyword, i);
                }
            }
        }
        return colIndexMap;
    }

    private List<String> getHeaders(String fileId) throws IOException{
        ValueRange headerRange = sheetClient.getRange(fileId, "1:1");
        return GoogleSheetParser.readHeaders(headerRange);

    }

    private List<List<String>> getsheetAllRows(String fileId) throws IOException{
        // Map<String, Integer> colIndexMap = new HashMap<>();
        List<String> headers = getHeaders(fileId);
        String range = "A2:"+colIndexToLetter(headers.size());
        // List<Integer> categoriesColumn = getCategoriesColumns(fileId);
        
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
        // List<String> headers = readSheetHeader(fileId);
        List<Integer> matchingColumnIndices = new ArrayList<>();
        for(int i = 0; i < headers.size(); i++){
            if(headers.get(i).equalsIgnoreCase(CATEGORY_KEYWORD)){
                matchingColumnIndices.add(i);
            }
        }     
        return matchingColumnIndices;
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
}