import at.ac.tuwien.ifs.sge.util.tree.DoubleLinkedTree;
import at.ac.tuwien.ifs.sge.util.tree.Tree;

import java.util.*;
import java.util.stream.Collectors;

public class MyDoubleLinkedTree implements Tree<McGameNode> {
    /* A general copy of DoubleLinkedTree with minor adjustments */

    public Iterator<McGameNode> myChildrenIterator() {

        ArrayList<MyDoubleLinkedTree> list = new ArrayList<>();
        for (Tree<McGameNode> l:getChildren()) {
            if (l instanceof MyDoubleLinkedTree){
                list.add((MyDoubleLinkedTree) l);
            }
        }
        Iterator<MyDoubleLinkedTree> iter = list.iterator();
        return new Iterator<McGameNode>() {
            int i = -1;
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public McGameNode next() {
                i++;
                return iter.next().getNode();
            }

            @Override
            public void remove() {
                dropChild(i--);
            }
        };
    }

    public Set<Integer> getChildrenByIdWhenReinforcing(){
        return getChildren().stream().map(a -> a.getNode().getGame().getPreviousAction().reinforcedId()).collect(Collectors.toSet());
    }

    private MyDoubleLinkedTree parent = null;
    private McGameNode node = null;
    private List<MyDoubleLinkedTree> children;

    public MyDoubleLinkedTree() {
        parent = null;
        node = null;
        children = new ArrayList<>();
    }

    public MyDoubleLinkedTree(McGameNode elem) {
        parent = null;
        node = elem;
        children = new ArrayList<>();
    }

    public MyDoubleLinkedTree(Tree<McGameNode> tree) {
        this.setParent(tree.getParent());
        node = tree.getNode();
        children = new ArrayList<>();
        for (Tree<McGameNode> child : tree.getChildren()) {
            if (child instanceof MyDoubleLinkedTree) {
                children.add((MyDoubleLinkedTree) child);
            } else {
                children.add(new MyDoubleLinkedTree(child));
            }
        }
    }

    @Override
    public Iterator<McGameNode> preIterator() {
        Deque<MyDoubleLinkedTree> outerStack = new ArrayDeque<>();
        if (node != null) {
            outerStack.add(this);
        }

        return new Iterator<McGameNode>() {

            Deque<MyDoubleLinkedTree> stack = new ArrayDeque<>(outerStack);
            MyDoubleLinkedTree current;

            @Override
            public boolean hasNext() {
                return !stack.isEmpty();
            }

            @Override
            public McGameNode next() {
                current = stack.pop();

                for (int i = current.children.size() - 1; i >= 0; i--) {
                    stack.push(current);
                }

                return current.getNode();
            }
        };
    }

    @Override
    public Iterator<Tree<McGameNode>> preTreeIterator() {
        Deque<MyDoubleLinkedTree> outerStack = new ArrayDeque<>();
        if (node != null) {
            outerStack.add(this);
        }

        return new Iterator<Tree<McGameNode>>() {

            Deque<MyDoubleLinkedTree> stack = new ArrayDeque<>(outerStack);
            MyDoubleLinkedTree current;

            @Override
            public boolean hasNext() {
                return !stack.isEmpty();
            }

            @Override
            public Tree<McGameNode> next() {
                current = stack.pop();

                for (int i = current.children.size() - 1; i >= 0; i--) {
                    stack.push(current);
                }

                return current;
            }
        };
    }

    @Override
    public Iterator<McGameNode> postIterator() {

        Deque<MyDoubleLinkedTree> outerStack = new ArrayDeque<>();
        outerStack.add(this);

        return new Iterator<McGameNode>() {

            Deque<MyDoubleLinkedTree> stack = new ArrayDeque<>(outerStack);
            MyDoubleLinkedTree current = null;
            MyDoubleLinkedTree lastParent = null;
            McGameNode next;

            @Override
            public boolean hasNext() {
                return !stack.isEmpty() || (current != null && current.node != null);
            }

            @Override
            public McGameNode next() {
                while (!stack.isEmpty()) {
                    current = stack.peek();
                    if (current == lastParent || current.isLeaf()) {
                        next = current.node;
                        stack.pop();
                        lastParent = current.parent;
                        break;
                    } else {
                        for (int i = children.size() - 1; i >= 0; i--) {
                            stack.push(children.get(i));
                        }
                    }
                }
                current = null;
                return next;
            }
        };
    }

    @Override
    public Iterator<Tree<McGameNode>> postTreeIterator() {

        Deque<MyDoubleLinkedTree> outerStack = new ArrayDeque<>();
        outerStack.add(this);

        return new Iterator<Tree<McGameNode>>() {

            Deque<MyDoubleLinkedTree> stack = new ArrayDeque<>(outerStack);
            MyDoubleLinkedTree current = null;
            MyDoubleLinkedTree lastParent = null;

            @Override
            public boolean hasNext() {
                return !stack.isEmpty() || (current != null);
            }

            @Override
            public Tree<McGameNode> next() {
                while (!stack.isEmpty()) {
                    current = stack.peek();
                    if (current == lastParent || current.isLeaf()) {
                        Tree<McGameNode> toReturn = current;
                        current = null;
                        stack.pop();
                        lastParent = current.parent;
                        return toReturn;
                    } else {
                        for (int i = children.size() - 1; i >= 0; i--) {
                            stack.push(children.get(i));
                        }
                    }
                }
                return null;
            }
        };
    }

    @Override
    public Iterator<McGameNode> levelIterator() {

        Deque<MyDoubleLinkedTree> outerQueue = new ArrayDeque<>();
        outerQueue.add(this);

        return new Iterator<McGameNode>() {

            Deque<MyDoubleLinkedTree> queue = new ArrayDeque<>(outerQueue);
            MyDoubleLinkedTree current;

            @Override
            public boolean hasNext() {
                return !queue.isEmpty();
            }

            @Override
            public McGameNode next() {
                current = queue.remove();
                queue.addAll(current.children);
                return current.node;
            }
        };
    }

    @Override
    public Iterator<Tree<McGameNode>> levelTreeIterator() {

        Deque<MyDoubleLinkedTree> outerQueue = new ArrayDeque<>();
        outerQueue.add(this);

        return new Iterator<Tree<McGameNode>>() {

            Deque<MyDoubleLinkedTree> queue = new ArrayDeque<>(outerQueue);
            MyDoubleLinkedTree current;

            @Override
            public boolean hasNext() {
                return !queue.isEmpty();
            }

            @Override
            public Tree<McGameNode> next() {
                current = queue.remove();
                queue.addAll(current.children);
                return current;
            }
        };
    }


    @Override
    public McGameNode getNode() {
        return node;
    }

    @Override
    public Tree<McGameNode> setNode(McGameNode e) {
        node = e;
        return this;
    }

    @Override
    public MyDoubleLinkedTree getParent() {
        return parent;
    }

    @Override
    public void setParent(Tree<McGameNode> parent) {
        if (parent instanceof DoubleLinkedTree) {
            this.parent = (MyDoubleLinkedTree) parent;
        } else {
            this.parent = new MyDoubleLinkedTree(parent);
        }
    }

    public void MySetParent(MyDoubleLinkedTree parent) {
        if (parent instanceof MyDoubleLinkedTree){
            this.parent = parent;
        } else {
            this.parent = new MyDoubleLinkedTree(parent);
        }
    }

    @Override
    public Tree<McGameNode> getChild(int index) {
        return children.get(index);
    }

    @Override
    public List<Tree<McGameNode>> getChildren() {
        return new ArrayList<>(children);
    }

    public List<MyDoubleLinkedTree> myGetChildren() {
        return new ArrayList<MyDoubleLinkedTree>(children);
    }

    @Override
    public void dropParent() {
        parent = null;
    }

    @Override
    public void dropChild(int n) {
        children.remove(n);
    }

    @Override
    public void dropChildren() {
        children.clear();
    }

    @Override
    public void clear() {
        parent = null;
        node = null;
        children.clear();
    }

    @Override
    public Iterator<McGameNode> iterator() {
        return preIterator();
    }

    @Override
    public boolean remove(Object o) {
        if (o == null) {
            return false;
        }

        if (o.equals(node)) {
            removeBranch();
            return true;
        }

        boolean removed = false;
        for (MyDoubleLinkedTree child : children) {
            removed = removed || child.remove(o);
        }

        return removed;
    }

    @Override
    public void sort(Comparator<McGameNode> comparator) {
        children.sort((o1, o2) -> comparator.compare(o1.node, o2.node));
    }

    @Override
    public int size() {
        int size = 0;
        if (node != null) {
            size = 1;
        }
        for (MyDoubleLinkedTree child : children) {
            size += child.size();
        }
        return size;
    }

    public boolean removeBranch() {
        boolean removed = false;
        for (MyDoubleLinkedTree child : children) {
            child.parent = this.parent;
            removed = true;
        }
        return removed;
    }


    @Override
    public boolean add(McGameNode e) {
        return add(new MyDoubleLinkedTree(e));
    }

    public boolean add(Tree<McGameNode> leaf) {
        if (leaf instanceof MyDoubleLinkedTree) {
            MyDoubleLinkedTree doubleLinkedTree = (MyDoubleLinkedTree) leaf;
            doubleLinkedTree.parent = this;
            return children.add(doubleLinkedTree);
        }

        leaf.setParent(this);
        return children.add(new MyDoubleLinkedTree(leaf));
    }

}
