// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack;

import dev.bscs.bacnet.bacnetsc.SCOption;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuthData {

    private List<SCOption> options;

    public static AuthData makeSecurePath() {
        return new AuthData(new SCOption(SCOption.TYPE_SECURE_PATH,true));
    }

    public AuthData(List<SCOption> options) {
        this.options = (options != null)? new ArrayList<>(options) : new ArrayList<>();
    }

    public AuthData(SCOption option) {
        this.options = new ArrayList<>();
        this.options.add(option);
    }

    public void           setOptions(List<SCOption>options) { this.options = new ArrayList<>(options); }

    public List<SCOption> getOptions() { return new ArrayList<>(options); }

    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("{");
        if (options != null) for (SCOption option : options) s.append(option.toString());
        s.append("}");
        return s.toString();
    }
}
