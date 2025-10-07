package br.ifsul;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class FilmeDataInitializer implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilmeDataInitializer.class);
    private static final String MOVIE_DATA_URL = "https://raw.githubusercontent.com/vega/vega-datasets/master/data/movies.json";
    private static final long TARGET_TOTAL = 1_000L;
    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})$");

    private final FilmeRepository filmeRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public FilmeDataInitializer(FilmeRepository filmeRepository, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.filmeRepository = filmeRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        long existentes = filmeRepository.count();
        long faltantes = TARGET_TOTAL - existentes;
        if (faltantes <= 0) {
            LOGGER.debug("Filmes já possuem {} registros. Nenhuma importação necessária.", existentes);
            return;
        }

        try {
            String payload = restTemplate.getForObject(MOVIE_DATA_URL, String.class);
            if (payload == null || payload.isBlank()) {
                LOGGER.warn("Resposta vazia ao tentar importar filmes da API externa.");
                return;
            }

            ExternalMovie[] resposta = objectMapper.readValue(payload, ExternalMovie[].class);
            if (resposta == null || resposta.length == 0) {
                LOGGER.warn("Resposta vazia ao tentar importar filmes da API externa.");
                return;
            }

            List<Filme> novosFilmes = Arrays.stream(resposta)
                    .filter(movie -> movie.title() != null && !movie.title().isBlank())
                    .map(this::converterParaFilme)
                    .limit(faltantes)
                    .toList();

            if (novosFilmes.isEmpty()) {
                LOGGER.warn("Nenhum filme válido encontrado durante a importação.");
                return;
            }

            filmeRepository.saveAll(novosFilmes);
            LOGGER.info("Importados {} filmes da fonte externa. Total atual: {}", novosFilmes.size(),
                    filmeRepository.count());
        } catch (RestClientException | IOException e) {
            LOGGER.error("Falha ao importar filmes da API externa: {}", e.getMessage(), e);
        }
    }

    private Filme converterParaFilme(ExternalMovie movie) {
        String diretor = Optional.ofNullable(movie.director())
                .filter(value -> !value.isBlank())
                .orElse("Diretor desconhecido");
        String ano = extrairAno(movie.releaseDate());
        if (ano == null) {
            ano = "Não informado";
        }
        return new Filme(null, movie.title().trim(), diretor, ano);
    }

    private String extrairAno(String releaseDate) {
        if (releaseDate == null) {
            return null;
        }
        Matcher matcher = YEAR_PATTERN.matcher(releaseDate.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExternalMovie(
            @JsonProperty("Title") String title,
            @JsonProperty("Director") String director,
            @JsonProperty("Release Date") String releaseDate) {
    }
}
