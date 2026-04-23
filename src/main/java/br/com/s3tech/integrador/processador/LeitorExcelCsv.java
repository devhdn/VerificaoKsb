package br.com.s3tech.integrador.processador;

import org.apache.poi.ss.usermodel.*;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;

public class LeitorExcelCsv {
    public List<String[]> processarFicheiro(Path caminho) {
        List<String[]> dados = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(caminho.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter df = new DataFormatter(); // Mantém o formato visual para o resto (ex: valores)

            for (Row row : sheet) {
                // Ignora o cabeçalho (linha 0)
                if (row.getRowNum() == 0) continue;

                int maxColunas = row.getLastCellNum();
                if (maxColunas < 0) continue; // Previne erros em linhas vazias

                String[] linha = new String[maxColunas];

                for (int i = 0; i < maxColunas; i++) {
                    Cell cell = row.getCell(i);

                    if (cell == null) {
                        linha[i] = "";
                    }
                    // 1. O TRUQUE DE MESTRE: Se o Excel sabe que é uma data, formatamos nós à força!
                    else if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                        Date dataExcel = cell.getDateCellValue();
                        linha[i] = new SimpleDateFormat("dd/MM/yyyy").format(dataExcel);
                    }
                    // 2. Se for texto, fórmulas ou números normais, deixamos o DataFormatter atuar
                    else {
                        linha[i] = df.formatCellValue(cell).trim();
                    }
                }
                dados.add(linha);
            }
        } catch (Exception e) {
            System.err.println("[ERRO AO LER FICHEIRO] Falha ao processar a folha de cálculo: " + e.getMessage());
        }

        return dados;
    }
}