/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.admin.model;

import java.io.File;
import java.util.List;
import lombok.Data;

@Data
public class DTOFileNavigation {

    private List<String> folders;
    private List<String> cfgfiles;
    private String current;
    private String separator = File.separator;
    private List<String> roots;
}
