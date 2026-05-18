package org.codinjutsu.tools.jenkins;

import org.codinjutsu.tools.jenkins.model.Job;
import org.codinjutsu.tools.jenkins.model.JobType;
import org.codinjutsu.tools.jenkins.view.JenkinsTreeNode;
import org.junit.jupiter.api.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JenkinsTreeFillJobTreeTest {

    @Test
    void folderWithChildrenNotLoadedGetsLoadingNodeChild() {
        Job folder = folder().jobType(JobType.FOLDER).build();  // childrenLoaded=false by default

        DefaultMutableTreeNode node = nodeFor(folder);
        JenkinsTree.fillJobTree(folder, node);

        assertThat(node.getChildCount()).isEqualTo(1);
        assertThat(childUserObject(node, 0)).isInstanceOf(JenkinsTreeNode.LoadingNode.class);
    }

    @Test
    void multiBranchWithChildrenNotLoadedGetsLoadingNodeChild() {
        Job pipeline = folder().jobType(JobType.MULTI_BRANCH).build();

        DefaultMutableTreeNode node = nodeFor(pipeline);
        JenkinsTree.fillJobTree(pipeline, node);

        assertThat(node.getChildCount()).isEqualTo(1);
        assertThat(childUserObject(node, 0)).isInstanceOf(JenkinsTreeNode.LoadingNode.class);
    }

    @Test
    void loadedFolderWithChildrenShowsRealChildren() {
        Job child = job().build();
        Job folder = folder().jobType(JobType.FOLDER).childrenLoaded(true).build();
        folder.setNestedJobs(List.of(child));

        DefaultMutableTreeNode node = nodeFor(folder);
        JenkinsTree.fillJobTree(folder, node);

        assertThat(node.getChildCount()).isEqualTo(1);
        assertThat(childUserObject(node, 0)).isInstanceOf(JenkinsTreeNode.JobNode.class);
        assertThat(((JenkinsTreeNode.JobNode) childUserObject(node, 0)).job()).isEqualTo(child);
    }

    @Test
    void loadedEmptyFolderHasNoChildren() {
        Job folder = folder()
                .jobType(JobType.FOLDER)
                .childrenLoaded(true)
                .build();  // nestedJobs is empty list

        DefaultMutableTreeNode node = nodeFor(folder);
        JenkinsTree.fillJobTree(folder, node);

        assertThat(node.getChildCount()).isZero();
    }

    @Test
    void fillJobTreeClearsExistingChildrenBeforePopulating() {
        Job folder = folder().jobType(JobType.FOLDER).build();
        DefaultMutableTreeNode node = nodeFor(folder);
        node.add(new DefaultMutableTreeNode("stale"));

        JenkinsTree.fillJobTree(folder, node);

        assertThat(node.getChildCount()).isEqualTo(1);
        assertThat(childUserObject(node, 0)).isInstanceOf(JenkinsTreeNode.LoadingNode.class);
    }

    // --- helpers ---

    private static Job.JobBuilder folder() {
        return Job.builder().name("folder").fullName("folder").url("http://jenkins/job/folder/");
    }

    private static Job.JobBuilder job() {
        return Job.builder().name("child").fullName("child").url("http://jenkins/job/child/");
    }

    private static DefaultMutableTreeNode nodeFor(Job job) {
        return new DefaultMutableTreeNode(new JenkinsTreeNode.JobNode(job), true);
    }

    private static Object childUserObject(DefaultMutableTreeNode parent, int index) {
        return ((DefaultMutableTreeNode) parent.getChildAt(index)).getUserObject();
    }
}
