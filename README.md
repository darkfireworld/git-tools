# git-tools

## 安装步骤

1. 运行`mvn package`
2. 提取`java-git-tools.jar` 和 `guava.jar` 到新建的`git-tools/`中
3. 创建`git-tools.bat`

bat文件基本格式为：

```
@echo off
java -cp %~DP0git-tools-1.0-SNAPSHOT.jar;%~DP0guava-19.0.jar org.darkfireworld.GitTools %1 %2 %3 %4 %5 %6 %7 %8

```

## 命令

运行`git-tools help`查看支持的命令

## 注意

运行环境需要git和jdk支持。