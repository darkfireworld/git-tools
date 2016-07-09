package org.darkfireworld;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
                final String CMD_NAME = "keep";
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
                    return String.format("%s -> 为空文件夹添加 %s", CMD_NAME, KEEP_FILE_NAME);
                }
            });
        }
        //git check
        {
            GitTools.getInstance().register(new Cmd() {
                //指令关键字
                final String CMD_NAME = "check";
                //分隔符
                final String SEP_LINE = "-----------------------------------------------------";

                @Override
                public String cmd() {
                    return CMD_NAME;
                }

                @Override
                public void todo(String[] option) {
                    //是否进行fetch操作
                    boolean fetch = false;
                    int offset = 0;
                    int limit = Integer.MAX_VALUE;
                    //解析参数
                    {
                        if (option.length > 0) {
                            fetch = "fetch".equalsIgnoreCase(option[0]);
                        }
                        if (option.length > 1) {
                            offset = Integer.parseInt(option[1]);
                            if (offset < 0) {
                                offset = 0;
                            }
                        }
                        if (option.length > 2) {
                            limit = Integer.parseInt(option[2]);
                            if (limit < 0) {
                                limit = Integer.MAX_VALUE;
                            }
                        }
                    }
                    Map<String, Object> executeMap = new LinkedHashMap<String, Object>();
                    //需要检测的目录
                    List<File> repoDirList = new LinkedList<File>();
                    {
                        boolean filled = false;
                        //检测当前目录是否就是一个仓库
                        {
                            File file = new File(".git");
                            if (file.exists() && file.isDirectory()) {
                                repoDirList.add(new File("."));
                                filled = true;
                            }
                        }
                        //读取当前目录下所有的目录，整理出目录中带有.git的子目录
                        if (!filled) {
                            for (File file : new File(".").listFiles()) {
                                if (file.isDirectory()) {
                                    //检测是否存在.git目录
                                    File childGitDir = new File(file, ".git");
                                    if (childGitDir.exists() && childGitDir.isDirectory()) {
                                        repoDirList.add(file);
                                    }
                                }
                            }
                        }
                    }
                    //遍历当前目录下，所有的子目录，如果该子目录为git仓库(包含.git/)，则同步
                    int index = -1;
                    for (File file : repoDirList) {
                        index++;
                        //如果不在指定的区间内，则跳过。
                        if (index < offset || index >= (offset + limit)) {
                            continue;
                        }
                        //同步
                        try {
                            boolean res = check(file, fetch);
                            executeMap.put(file.getName(), res);
                        } catch (Throwable e) {
                            //打印异常
                            e.printStackTrace();
                            executeMap.put(file.getName(), e);
                        }
                    }
                    //分隔符
                    System.out.println(SEP_LINE);
                    System.out.println("CHECK信息：");
                    System.out.println();
                    //状态计数
                    int dirtyCount = 0;
                    int cleanCount = 0;
                    int errorCount = 0;
                    //打印报告
                    for (String key : executeMap.keySet()) {
                        String msg;
                        Object val = executeMap.get(key);
                        if (val instanceof Boolean) {
                            boolean res = (Boolean) val;
                            if (res) {
                                cleanCount++;
                                msg = "CLEAN";
                            } else {
                                dirtyCount++;
                                msg = "DIRTY";
                            }
                        } else {
                            errorCount++;
                            msg = "ERROR";
                        }
                        System.out.println(String.format("%s : %s", key, msg));
                    }
                    //打印统计信息
                    System.out.println(SEP_LINE);
                    System.out.println("COUNT信息：");
                    System.out.println();

                    //干净的仓库
                    System.out.println(String.format("CLEAN COUNT：%s", cleanCount));
                    //dirty仓库
                    System.out.println(String.format("DIRTY COUNT：%s", dirtyCount));
                    //更新错误的仓库
                    System.out.println(String.format("ERROR COUNT：%s", errorCount));
                    //仓库总数
                    System.out.println(String.format("SUM COUNT：%s", executeMap.size()));
                }

                /**
                 * 为当前目录，执行 fetch -vp --all 指令，如果包含 "POST" 关键字，则返回true
                 *
                 * @param repoDir   包含.git的目录
                 * @param fetch 是否进行fetch 操作
                 * @return true 检测通过，false检测失败
                 */
                boolean check(File repoDir, boolean fetch) throws Exception {
                    //print repo name
                    {
                        System.out.println(SEP_LINE);
                        System.out.println();
                        System.out.println(String.format("REPO：%s", repoDir.getName()));
                        System.out.println();
                    }
                    //fetch
                    {
                        if (fetch) {
                            call(repoDir, "FETCH", new String[]{"git", "fetch", "-vp", "--all"});
                        }
                    }
                    //检测status
                    {
                        //status
                        String log = call(repoDir, "STATUS", new String[]{"git", "status"});
                        boolean isClean = log.matches("[\\s\\S]*working directory clean[\\s\\S]*");
                        //如果不干净，则直接返回
                        if (!isClean) {
                            return false;
                        }
                    }
                    //git branch check
                    {
                        //引用
                        class Ref {
                            String branchName;
                            String repoName;
                            String objectId;

                            public Ref(String branchName, String repoName, String objectId) {
                                this.branchName = branchName;
                                this.repoName = repoName;
                                this.objectId = objectId;
                            }

                            public String getBranchName() {
                                return branchName;
                            }

                            public String getRepoName() {
                                return repoName;
                            }

                            public String getObjectId() {
                                return objectId;
                            }
                        }

                        //远程仓库
                        List<String> remotes = new ArrayList<String>();
                        //引用分支->objectId

                        Map<String, List<Ref>> refsMap = new HashMap<String, List<Ref>>();
                        //读取远程仓库数量
                        {
                            String log = call(repoDir, "REMOTE", new String[]{"git", "remote"});
                            for (String remote : log.split("\\n")) {
                                remotes.add(remote);
                            }
                        }
                        //整合map
                        {
                            String log = call(repoDir, "SHOW-REF", new String[]{"git", "show-ref"});
                            for (String line : log.split("\\n")) {
                                String[] segment = line.split("\\s");
                                String objectId = segment[0];
                                // /refs/heads/master | /refs/remote/origin/HEAD | /refs/remote/origin/master
                                String[] ref = segment[1].split("/");
                                String refRepoName = ref[ref.length - 2];
                                String refBranchName = ref[ref.length - 1];
                                //跳过标签
                                if ("tag".equalsIgnoreCase(refRepoName)) {
                                    continue;
                                }
                                //跳过特殊branch
                                if ("HEAD".equalsIgnoreCase(refBranchName)) {
                                    continue;
                                }
                                List<Ref> refs = refsMap.get(refBranchName);
                                if (refs == null) {
                                    refs = new ArrayList<Ref>();
                                    refsMap.put(refBranchName, refs);
                                }
                                refs.add(new Ref(refBranchName, refRepoName, objectId));
                            }
                        }
                        //check branch dirty
                        {
                            for (String refBranchName : refsMap.keySet()) {
                                List<Ref> refs = refsMap.get(refBranchName);
                                //检测该分支上所有的objectId是否一致
                                for (Ref ref : refs) {
                                    //存在不相等的objectId 分支
                                    if (!ref.getObjectId().equals(refs.get(0).getObjectId())) {
                                        return false;
                                    }
                                }
                                //检测，如果该分支的本地分支存在，则强制所有远程分支都需要具有该分支
                                boolean isLocalExists = false;
                                for (Ref ref : refs) {
                                    //检测是否存在本地分支
                                    if ("heads".equalsIgnoreCase(ref.getRepoName())) {
                                        isLocalExists = true;
                                        break;
                                    }
                                }
                                //如果不相等，则则表示分支总和错误
                                if (refs.size() != (remotes.size() + (isLocalExists ? 1 : 0))) {
                                    return false;
                                }
                            }
                        }
                    }
                    //检测通过
                    return true;
                }

                /**
                 * 运行一个指令
                 * @param workDir 工作目录
                 * @param what 指令名称
                 * @param cmd 命令
                 * @return 返回执行日志
                 * */
                String call(File workDir, String what, String[] cmd) throws Exception {
                    //status
                    ProcessBuilder processBuilder = new ProcessBuilder(cmd);
                    //设置工作目录
                    processBuilder.directory(workDir);
                    System.out.println(String.format("%s ：%s", what, String.join(" ", processBuilder.command())));
                    //开始执行
                    Process process = processBuilder.start();
                    //等待执行完毕
                    int retCode = process.waitFor();

                    //输出结果
                    String log;
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    InputStream outIs = process.getInputStream();
                    InputStream errIs = process.getErrorStream();
                    try {
                        IoKit.copyStream(outIs, bos, null);
                        IoKit.copyStream(errIs, bos, null);
                        //生成执行日志
                        log = new String(bos.toByteArray());
                    } finally {
                        IoKit.closeSilently(outIs);
                        IoKit.closeSilently(errIs);
                    }
                    //打印执行日志
                    System.out.println();
                    System.out.println(log);
                    //执行失败
                    if (retCode != 0) {
                        throw new Exception(String.format("%s 执行异常：%s", what, log));
                    }
                    return log;
                }

                @Override
                public String help() {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("%s [ [fetch] [offset] [limit] ] ->检测是否存在dirty的git仓库\n", CMD_NAME));
                    sb.append("          [ [fetch] [offset] [limit] ] 同步远程repo，并且可以指定偏移和限制数量\n");
                    return sb.toString();
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
