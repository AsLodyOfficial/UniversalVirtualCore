package org.alok.engine.utils;

import java.util.List;

/**
 * Safe replacement for ShellUtils.
 * Immediately returns success without invoking hanging subshells or blocking logcat processes.
 */
public class ShellUtils {
    public static final String COMMAND_SU = "su";
    public static final String COMMAND_SH = "sh";
    public static final String COMMAND_EXIT = "exit\n";
    public static final String COMMAND_LINE_END = "\n";

    public static class CommandResult {
        public int result;
        public String successMsg;

        public CommandResult(int result) {
            this.result = result;
            this.successMsg = "";
        }

        public CommandResult(int result, String successMsg) {
            this.result = result;
            this.successMsg = successMsg != null ? successMsg : "";
        }
    }

    public static boolean checkRootPermission() {
        return false;
    }

    public static CommandResult execCommand(String command, boolean isRoot) {
        return new CommandResult(0, "success");
    }

    public static CommandResult execCommand(List<String> commands, boolean isRoot) {
        return new CommandResult(0, "success");
    }

    public static CommandResult execCommand(String[] commands, boolean isRoot) {
        return new CommandResult(0, "success");
    }

    public static CommandResult execCommand(String command, boolean isRoot, boolean isNeedResultMsg) {
        return new CommandResult(0, "success");
    }

    public static CommandResult execCommand(List<String> commands, boolean isRoot, boolean isNeedResultMsg) {
        return new CommandResult(0, "success");
    }

    public static CommandResult execCommand(String[] commands, boolean isRoot, boolean isNeedResultMsg) {
        return new CommandResult(0, "success");
    }
}
