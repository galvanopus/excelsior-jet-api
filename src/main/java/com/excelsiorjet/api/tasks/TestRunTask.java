/*
 * Copyright (c) 2016, Excelsior LLC.
 *
 *  This file is part of Excelsior JET API.
 *
 *  Excelsior JET API is free software:
 *  you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Excelsior JET API is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Excelsior JET API.
 *  If not, see <http://www.gnu.org/licenses/>.
 *
*/
package com.excelsiorjet.api.tasks;

import com.excelsiorjet.api.cmd.*;
import com.excelsiorjet.api.log.AbstractLog;
import com.excelsiorjet.api.util.Txt;
import com.excelsiorjet.api.util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestRunTask {

    private static final String BOOTSTRAP_JAR = "bootstrap.jar";

    private final JetProject project;

    public TestRunTask(JetProject project) {
        this.project = project;
    }

    private String getTomcatClassPath(JetHome jetHome, File tomcatBin) throws JetTaskFailureException, IOException {
        File f = new File(tomcatBin, BOOTSTRAP_JAR);
        if (!f.exists()) {
            throw new JetTaskFailureException(Txt.s("TestRunTask.Tomcat.NoBootstrapJar.Failure", tomcatBin.getAbsolutePath()));
        }

        Manifest bootManifest;
        try {
            bootManifest = new JarFile(f).getManifest();
        } catch (IOException e) {
            throw new IOException(Txt.s("TestRunTask.Tomcat.FailedToReadBootstrapJar.Failure", tomcatBin.getAbsolutePath(), e.getMessage()), e);
        }

        ArrayList<String> classPath = new ArrayList<>();
        classPath.add(BOOTSTRAP_JAR);

        String bootstrapJarCP = bootManifest.getMainAttributes().getValue("CLASS-PATH");
        if (bootstrapJarCP != null) {
            classPath.addAll(Arrays.asList(bootstrapJarCP.split("\\s+")));
        }

        classPath.add(jetHome.getJetHome() + File.separator + "lib" + File.separator + "tomcat" + File.separator + "TomcatSupport.jar");
        return String.join(File.pathSeparator, classPath);
    }

    public List<String> getTomcatVMArgs() {
        String tomcatDir = project.tomcatInBuildDir().getAbsolutePath();
        return Arrays.asList(
                "-Djet.classloader.id.provider=com/excelsior/jet/runtime/classload/customclassloaders/tomcat/TomcatCLIDProvider",
                "-Dcatalina.base=" + tomcatDir,
                "-Dcatalina.home=" + tomcatDir,
                "-Djava.io.tmpdir=" + tomcatDir + File.separator + "temp",
                "-Djava.util.logging.config.file=../conf/logging.properties",
                "-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager"
        );
    }

    public void execute() throws JetTaskFailureException, IOException, CmdLineToolException {
        JetHome jetHome = project.validate();

        // creating output dirs
        File buildDir = project.createBuildDir();

        String classpath;
        List<String> additionalVMArgs;
        File workingDirectory;
        switch (project.appType()) {
            case PLAIN:
                List<ClasspathEntry> dependencies = project.copyDependencies();
                if (project.packageFilesDir().exists()) {
                    //application may access custom package files at runtime. So copy them as well.
                    Utils.copyQuietly(project.packageFilesDir().toPath(), buildDir.toPath());
                }

                classpath = String.join(File.pathSeparator,
                        dependencies.stream().map(d -> d.getFile().toString()).collect(Collectors.toList()));
                additionalVMArgs = Collections.emptyList();
                workingDirectory = buildDir;
                break;
            case TOMCAT:
                project.copyTomcatAndWar();
                workingDirectory = new File(project.tomcatInBuildDir(), "bin");
                classpath = getTomcatClassPath(jetHome, workingDirectory);
                additionalVMArgs = getTomcatVMArgs();
                break;
            default:
                throw new AssertionError("Unknown app type");
        }

        Utils.mkdir(project.execProfilesDir());

        XJava xjava = new XJava(jetHome);
        try {
            xjava.addTestRunArgs(new TestRunExecProfiles(project.execProfilesDir(), project.execProfilesName()))
                    .withLog(AbstractLog.instance(),
                            project.appType() == ApplicationType.TOMCAT) // Tomcat outputs to std error, so to not confuse users,
                    // we  redirect its output to std out in test run
                    .workingDirectory(workingDirectory);
        } catch (JetHomeException e) {
            throw new JetTaskFailureException(e.getMessage());
        }

        xjava.addArgs(additionalVMArgs);

        //add jvm args substituting $(Root) occurences with buildDir
        xjava.addArgs(Stream.of(project.jvmArgs())
                .map(s -> s.replace("$(Root)", buildDir.getAbsolutePath()))
                .collect(Collectors.toList())
        );

        xjava.arg("-cp");
        xjava.arg(classpath);
        xjava.arg(project.mainClass());
        String cmdLine = xjava.getArgs().stream()
                .map(arg -> arg.contains(" ") ? '"' + arg + '"' : arg)
                .collect(Collectors.joining(" "));

        AbstractLog.instance().info(Txt.s("TestRunTask.Start.Info", cmdLine));

        int errCode = xjava.execute();
        String finishText = Txt.s("TestRunTask.Finish.Info", errCode);
        if (errCode != 0) {
            AbstractLog.instance().warn(finishText);
        } else {
            AbstractLog.instance().info(finishText);
        }
    }
}