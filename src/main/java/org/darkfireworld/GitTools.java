package org.darkfireworld;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Git 帮助
 */
public class GitTools {

    //默认指令
    static final String DEFAULT_HELP_CMD = "help";
    static volatile GitTools instance;
    /**
     * 命令
     */
    Map<String, Cmd> cmdMap = new ConcurrentHashMap<String, Cmd>();

    private GitTools() {
    }

    public static void main(String[] args) {
        //注册
        //help
        {
            GitTools.getInstance().register(new Cmd() {
                @Override
                public String cmd() {
                    return DEFAULT_HELP_CMD;
                }

                @Override
                public void todo(String[] option) {
                    //读取所有注册的CMD
                    List<Cmd> cmdList = GitTools.getInstance().touch();
                    for (Cmd cmd : cmdList) {
                        System.out.println(cmd.help());
                    }
                }

                @Override
                public String help() {
                    return String.format("%s -> 帮助", DEFAULT_HELP_CMD);
                }
            });
        }
        //git keep
        {
            GitTools.getInstance().register(new Cmd() {
                final String CMD_NAME = "gitkeep";
                final String KEEP_FILE_NAME = ".gitkeep";

                @Override
                public String cmd() {
                    return CMD_NAME;
                }

                @Override
                public void todo(String[] option) {
                    try {
                        fuck(new File("."), KEEP_FILE_NAME);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    System.out.println("FINISH");
                }

                /**
                 * 添加/删除 指定空文件
                 * */

                void fuck(File file, String emptyFileName) throws IOException {
                    if (!(file.getName().startsWith(".git")
                            || file.getName().startsWith(".svn"))
                            && file.exists() && file.isDirectory()) {
                        //若目录下没有文件则直接删除
                        if (file.listFiles().length == 0) {
                            //创建空文件
                            File newFile = new File(file, emptyFileName);
                            newFile.createNewFile();
                            System.out.println(String.format("[create] %s", newFile.getAbsolutePath()));
                        } else {
                            //判断该目录下是否存在空文件s
                            File emptyFile = new File(file, emptyFileName);
                            if (emptyFile.exists() && file.listFiles().length > 1) {
                                //存在空文件，且还有其他文件，则删除这个空文件
                                emptyFile.delete();
                                System.out.println(String.format("[delete] %s", emptyFile.getAbsolutePath()));
                            }
                            //进入子目录检测
                            for (File file1 : file.listFiles()) {
                                fuck(file1, emptyFileName);
                            }
                        }
                    }
                }

                @Override
                public String help() {
                    return String.format("%s -> 遍历当前目录下所有目录，为空文件夹添加 %s", CMD_NAME, KEEP_FILE_NAME);
                }
            });
        }
        //git fetch
        {
            GitTools.getInstance().register(new Cmd() {
                //指令关键字
                final String CMD_NAME = "fetch";
                //更新关键字
                final String KEY_REGEX = "[\\s\\S]*POST[\\s\\S]*";
                //分隔符
                final String SEP_LINE = "-----------------------------------------------------";

                @Override
                public String cmd() {
                    return CMD_NAME;
                }

                @Override
                public void todo(String[] option) {
                    //执行结果信息, value->Boolean(true 存在更新，false 不存在更新) | Exception(执行异常)
                    Map<String, Object> executeMap = new LinkedHashMap<String, Object>();
                    //遍历当前目录下，所有的子目录，如果该子目录为git仓库(包含.git/)，则同步
                    for (File file : (new File(".")).listFiles()) {
                        if (file.isDirectory()) {
                            //检测是否存在.git目录
                            File childGitDir = new File(file, ".git");
                            if (childGitDir.exists() && childGitDir.isDirectory()) {
                                //同步
                                try {
                                    boolean res = fetch(childGitDir);
                                    executeMap.put(file.getName(), res);
                                } catch (Throwable e) {
                                    //打印异常
                                    e.printStackTrace();
                                    executeMap.put(file.getName(), e);
                                }
                            }
                        }
                    }
                    //分隔符
                    System.out.println(SEP_LINE);
                    System.out.println("FETCH信息：");
                    System.out.println();
                    //状态计数
                    int updateCount = 0;
                    int sameCount = 0;
                    int errorCount = 0;
                    //打印报告
                    for (String key : executeMap.keySet()) {
                        String msg;
                        Object val = executeMap.get(key);
                        if (val instanceof Boolean) {
                            boolean res = (Boolean) val;
                            if (res) {
                                updateCount++;
                                msg = "UPDATE";
                            } else {
                                sameCount++;
                                msg = "SAME";
                            }
                        } else {
                            errorCount++;
                            msg = "ERROR";
                        }
                        System.out.println(String.format("%s : %s", key, msg));
                    }
                    //打印统计信息
                    System.out.println(SEP_LINE);
                    System.out.println("统计信息：");
                    System.out.println();

                    //需要更新的仓库
                    System.out.println(String.format("UPDATE COUNT：%s", updateCount));
                    //没有更新的仓库
                    System.out.println(String.format("SAME COUNT：%s", sameCount));
                    //更新错误的仓库
                    System.out.println(String.format("ERROR COUNT：%s", errorCount));
                    //仓库总数
                    System.out.println(String.format("SUM COUNT：%s", executeMap.size()));
                }

                /**
                 * 为当前目录，执行 fetch -vp --all 指令，如果包含 "POST" 关键字，则返回true
                 *
                 * @param dir 指向.git的路径
                 * @return true 同步的时候发现关键字
                 * */
                boolean fetch(File dir) throws Exception {
                    //打印目录
                    ProcessBuilder processBuilder = new ProcessBuilder("git", String.format("--git-dir=%s", dir.getCanonicalPath()), "fetch", "-vp", "--all");
                    //打印执行日志
                    System.out.println(SEP_LINE);
                    System.out.println(String.format("CMD：%s", Joiner.on(" ").join(processBuilder.command())));
                    //开始执行
                    Process process = processBuilder.start();
                    //等待执行完毕
                    int retCode = process.waitFor();

                    //执行日志
                    StringBuilder sb = new StringBuilder();
                    //读取正常信息流
                    ByteArrayOutputStream normalBos = new ByteArrayOutputStream();
                    ByteStreams.copy(process.getInputStream(), normalBos);
                    //转换称为文本
                    sb.append(new String(normalBos.toByteArray()));

                    //读取错误日志
                    ByteArrayOutputStream errorBos = new ByteArrayOutputStream();
                    ByteStreams.copy(process.getErrorStream(), errorBos);
                    sb.append(new String(errorBos.toByteArray()));

                    String log = sb.toString();
                    //打印执行日志
                    System.out.println();
                    System.out.println(log);
                    //执行失败
                    if (retCode != 0) {
                        throw new Exception(String.format("git 执行异常：%s", log));
                    } else {
                        //返回正则匹配关键字信息
                        return log.matches(KEY_REGEX);
                    }
                }

                @Override
                public String help() {
                    return String.format("%s -> 遍历当前目录下所有子目录，为git的仓库执行：fetch -vp --all 命令", CMD_NAME);
                }
            });
        }
        //执行
        {
            GitTools.getInstance().cmd(args);
        }
    }

    public static GitTools getInstance() {
        if (instance == null) {
            synchronized (GitTools.class) {
                if (instance == null) {
                    instance = new GitTools();
                }
            }
        }
        return instance;
    }

    /**
     * 解析命令
     */
    void cmd(String[] args) {
        Cmd cmd = null;
        if (args.length > 0) {
            String name = args[0];
            cmd = cmdMap.get(name);
        }
        //尝试获取 默认
        if (cmd == null) {
            cmd = cmdMap.get(DEFAULT_HELP_CMD);
        }
        if (cmd == null) {
            throw new RuntimeException("不存在指令帮助模块");
        } else {
            String[] options;
            try {
                options = Arrays.copyOfRange(args, 1, args.length);
            } catch (Exception e) {
                options = new String[0];
            }
            cmd.todo(options);
        }
    }

    /**
     * 注册
     */
    public void register(Cmd cmd) {
        if (cmdMap.containsKey(cmd.cmd())) {
            throw new RuntimeException(String.format("重复注册: %s", cmd.cmd()));
        } else {
            cmdMap.put(cmd.cmd(), cmd);
        }
    }

    /**
     * 获取所有的CMD
     */
    public List<Cmd> touch() {
        List<Cmd> ret = new ArrayList<Cmd>();
        for (String key : cmdMap.keySet()) {
            ret.add(cmdMap.get(key));
        }
        return ret;
    }


    /**
     * 命令接口
     */
    interface Cmd {
        /**
         * 注册的命令
         */
        String cmd();

        /**
         * 需要去做的task
         */
        void todo(String[] option);

        /**
         * 帮助说明
         */
        String help();
    }

}
