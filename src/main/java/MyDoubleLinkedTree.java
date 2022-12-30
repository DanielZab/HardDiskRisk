import at.ac.tuwien.ifs.sge.util.tree.DoubleLinkedTree;
import at.ac.tuwien.ifs.sge.util.tree.Tree;

import java.util.*;
import java.util.stream.Collectors;

public class MyDoubleLinkedTree extends DoubleLinkedTree<McGameNode> {
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
}
