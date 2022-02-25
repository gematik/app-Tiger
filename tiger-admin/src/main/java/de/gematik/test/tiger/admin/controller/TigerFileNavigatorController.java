/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.admin.controller;

import de.gematik.test.tiger.admin.model.DTOFileNavigation;
import de.gematik.test.tiger.common.config.TigerConfigurationHelper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Slf4j
public class TigerFileNavigatorController {

    // TODO how to read this from application yaml file?
    @Value("#{'${fileNav.extensions:yaml,yml}'.split(',')}")
    private List<String> extensions;

    @GetMapping(value = "/navigator/folder", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public DTOFileNavigation getFolderInfo(@RequestParam("current") String currentFolder) throws IOException {
        File folder = new File(currentFolder);
        if (folder.listFiles() == null) {
            throw new IOException("Invalid folder '" + folder.getAbsolutePath() + "' given!");
        }
        try {
            final List<String> childFolders = new ArrayList<>();
            final List<String> configFiles = new ArrayList<>();
            childFolders.add("..");
            Arrays.stream(Objects.requireNonNull(folder.listFiles())).sorted().forEach(file -> {
                if (file.isDirectory()) {
                    childFolders.add(file.getName());
                } else {
                    String extension = StringUtils.substringAfterLast(file.getName(), '.').toLowerCase();
                    if (extensions.contains(extension)) {
                        configFiles.add(file.getName());
                    }
                }
            });
            DTOFileNavigation fileNavDTO = new DTOFileNavigation();
            fileNavDTO.setFolders(childFolders);
            fileNavDTO.setCfgfiles(configFiles);
            fileNavDTO.setCurrent(Path.of(folder.getAbsolutePath()).normalize().toFile().getAbsolutePath());
            fileNavDTO.setRoots(Arrays.stream(File.listRoots()).map(File::getPath).collect(Collectors.toList()));
            return fileNavDTO;
        } catch (Exception e) {
            throw new IOException("Unable to parse folder '" + currentFolder + "'!", e);
        }
    }


}
