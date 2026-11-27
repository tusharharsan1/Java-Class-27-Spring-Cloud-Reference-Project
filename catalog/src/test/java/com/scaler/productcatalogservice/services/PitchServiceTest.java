package com.scaler.productcatalogservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import com.scaler.productcatalogservice.clients.GroqClient;
import com.scaler.productcatalogservice.dtos.GroqChatResponseDto;
import com.scaler.productcatalogservice.models.Category;
import com.scaler.productcatalogservice.models.Product;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PitchServiceTest {

    @Mock
    private GroqClient groqClient;

    @InjectMocks
    private PitchService pitchService;

    private Product mascara() {
        Category category = new Category();
        category.setName("beauty");
        Product product = new Product();
        product.setName("Mascara");
        product.setPrice(new BigDecimal("9.99"));
        product.setCategory(category);
        return product;
    }

    private GroqChatResponseDto replyWith(String text) {
        GroqChatResponseDto.Message message = new GroqChatResponseDto.Message();
        message.setContent(text);
        GroqChatResponseDto.Choice choice = new GroqChatResponseDto.Choice();
        choice.setMessage(message);
        GroqChatResponseDto response = new GroqChatResponseDto();
        response.setChoices(List.of(choice));
        return response;
    }

    @Test
    void generatePitch_groqAnswers_returnsTheTrimmedText() {
        when(groqClient.chat(anyMap())).thenReturn(replyWith("  Glow up for $9.99!  "));

        String pitch = pitchService.generatePitch(mascara());

        assertThat(pitch).isEqualTo("Glow up for $9.99!");
    }

    @Test
    void generatePitch_groqFails_returnsTheTemplateSentence() {
        when(groqClient.chat(anyMap())).thenThrow(new RuntimeException("Groq is down"));

        String pitch = pitchService.generatePitch(mascara());

        assertThat(pitch).isEqualTo("New arrival: Mascara for just $9.99. Check it out!");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void generatePitch_emptyReply_returnsTheTemplateSentence(String content) {
        when(groqClient.chat(anyMap())).thenReturn(replyWith(content));

        String pitch = pitchService.generatePitch(mascara());

        assertThat(pitch).isEqualTo("New arrival: Mascara for just $9.99. Check it out!");
    }
}
