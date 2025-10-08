package br.ifsul;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class FilmeDataInitializer implements CommandLineRunner {

    private final FilmeRepository filmeRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${omdb.api.url:https://www.omdbapi.com/}")
    private String omdbApiUrl;

    @Value("${omdb.api.key:}")
    private String omdbApiKey;

    public FilmeDataInitializer(FilmeRepository filmeRepository, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.filmeRepository = filmeRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws IOException {
        if (filmeRepository.count() > 0) {
            return;
        }

        if (omdbApiKey == null || omdbApiKey.isBlank()) {
            return;
        }

        List<Filme> filmes = buscarFilmes();
        filmeRepository.saveAll(filmes);
    }

    private List<Filme> buscarFilmes() throws IOException {
        List<Filme> filmes = new ArrayList<>();
        
        String url = omdbApiUrl + "?s=movie&type=movie&page=1&apikey=" + encode(omdbApiKey);
        String payload = restTemplate.getForObject(url, String.class);
        
        if (payload == null || payload.isBlank()) {
            return filmes;
        }

        OmdbSearchResponse response = objectMapper.readValue(payload, OmdbSearchResponse.class);
        
        if (response.search() != null) {
            for (OmdbSearchMovie movie : response.search()) {
                if (movie.title() != null && !movie.title().isBlank()) {
                    String diretor = buscarDiretor(movie.imdbId());
                    Filme filme = new Filme(null, movie.title(), diretor, movie.year());
                    filmes.add(filme);
                }
            }
        }
        
        return filmes;
    }

    private String buscarDiretor(String imdbId) {
        if (imdbId == null || imdbId.isBlank()) {
            return "Desconhecido";
        }
        
        try {
            String url = omdbApiUrl + "?i=" + encode(imdbId) + "&apikey=" + encode(omdbApiKey);
            String payload = restTemplate.getForObject(url, String.class);
            
            if (payload != null && !payload.isBlank()) {
                OmdbDetailResponse detail = objectMapper.readValue(payload, OmdbDetailResponse.class);
                if (detail.director() != null && !detail.director().isBlank() && !"N/A".equals(detail.director())) {
                    return detail.director();
                }
            }
        } catch (Exception e) {
            // Ignora erro e retorna padrão
        }
        
        return "Desconhecido";
    }

    private String encode(String valor) {
        return URLEncoder.encode(valor != null ? valor : "", StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OmdbSearchResponse(
            @JsonProperty("Search") List<OmdbSearchMovie> search) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OmdbSearchMovie(
            @JsonProperty("Title") String title,
            @JsonProperty("Year") String year,
            @JsonProperty("imdbID") String imdbId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OmdbDetailResponse(
            @JsonProperty("Director") String director) {
    }
}