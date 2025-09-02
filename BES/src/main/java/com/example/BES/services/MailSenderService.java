package com.example.BES.services;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.BES.dtos.EmailRequestDto;
import com.example.BES.dtos.ParticpantsDto;
import com.example.BES.enums.EmailTemplates;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.EventParticipantId;
import com.example.BES.models.Participant;
import com.example.BES.respositories.GenreRepo;
import com.google.zxing.WriterException;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;

@Service
public class MailSenderService {
    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    EmailTemplates emailTemplates;

    @Autowired
    QrCodeService qrService;

    @Autowired
    GenreRepo genreRepo;

    public void sendEmail(String toEmail, String subject, String body){
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("bennylim0926@gmail.com");
        message.setTo(toEmail);
        message.setText(body);
        message.setSubject(subject);
        mailSender.send(message);
    }

    public void sendEmailWithAttachment(String eventName, Participant receiver, List<EventGenreParticipantId> ids) throws MessagingException, WriterException, IOException{
        EmailTemplates.Template template = emailTemplates.getEvents().get(normalizeKey(eventName)); 
        // SimpleMailMessage message = new SimpleMailMessage();
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, true);
        for(EventGenreParticipantId id : ids){
            String registerLink = String.format("https://travelling-translation-trap-vocals.trycloudflare.com/api/v1/event/register-participant/%d/%d/%d",id.getParticipantId(),id.getEventId(), id.getGenreId());
            byte[] sourceBytes = qrService.generateQrCode(registerLink, 150, 150);
            DataSource dataSource = new ByteArrayDataSource(sourceBytes, "image/jpeg");
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.setDataHandler(new DataHandler(dataSource));
            String genreName = genreRepo.findById(id.getGenreId()).orElse(null).getGenreName();
            attachmentPart.setFileName(String.format("%s.jpg", genreName));
            messageHelper.addAttachment(String.format("%s.jpg", genreName), dataSource);;
        }
            // use with MimeBodyPart
        messageHelper.setFrom("bennylim0926@gmail.com");
        messageHelper.setTo(receiver.getParticipantEmail());
        messageHelper.setText(template.getBody().replace("{name}", receiver.getParticipantName()));
        messageHelper.setSubject(template.getSubject());
        mailSender.send(mimeMessage);
    }

    // public void bulkSendEmailWithAttachment(EmailRequestDto dto) throws MessagingException{
    //     EmailTemplates.Template template = emailTemplates.getEvents().get(normalizeKey(dto.eventName)); 
    //     for(ParticpantsDto participant: dto.newPaidParticipants){
    //         // update the database record to mark sent as true
    //         byte[] sourceBytes = fetchRandomImage();
    //         sendEmailWithAttachment(
    //             participant.getEmail(),
    //             template.getSubject(),
    //             template.getBody().replace("{name}", participant.getName()), 
    //             sourceBytes);
    //     }
    // }
    
    public byte[] fetchRandomImage() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://picsum.photos/200";
        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
        return response.getBody(); // this is your random image as byte[]
    }

    public static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        // Replace space and dot with underscore
        return key.replaceAll("[ .]", "_");
    }
}
