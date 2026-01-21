package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ByteType;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.keyframes.factories.PoseKeyframeFactory;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PoseKeyframeUtils
{
    public static final IKeyframeFactory<PoseTransform> TRANSFORM_FACTORY = new IKeyframeFactory<PoseTransform>()
    {
        @Override
        public PoseTransform fromData(BaseType data)
        {
            return new PoseTransform();
        }

        @Override
        public BaseType toData(PoseTransform value)
        {
            return new ByteType((byte) 0);
        }

        @Override
        public PoseTransform interpolate(PoseTransform preA, PoseTransform a, PoseTransform b, PoseTransform postB, IInterp interp, float x)
        {
            PoseTransform t = new PoseTransform();

            t.lerp(a, 0);
            t.lerp(b, interp.interpolate(0, 1, x));

            return t;
        }

        @Override
        public PoseTransform createEmpty()
        {
            return new PoseTransform();
        }

        @Override
        public PoseTransform copy(PoseTransform value)
        {
            PoseTransform t = new PoseTransform();
            
            t.lerp(value, 0);
            
            return t;
        }
    };

    public static void generateChildren(UIKeyframeSheet sheet)
    {
        if (!sheet.children.isEmpty())
        {
            return;
        }

        if (sheet.channel.getFactory() instanceof PoseKeyframeFactory)
        {
            Set<String> bones = new HashSet<>();
            List<Keyframe> keyframes = sheet.channel.getKeyframes();

            for (Keyframe kf : keyframes)
            {
                if (kf.getValue() instanceof Pose)
                {
                    Pose pose = (Pose) kf.getValue();

                    bones.addAll(pose.transforms.keySet());
                }
            }

            List<String> sortedBones = new ArrayList<>(bones);
            sortedBones.sort(String::compareTo);

            for (String bone : sortedBones)
            {
                createBoneSheet(sheet, bone);
            }
        }
        else if (sheet.channel instanceof VirtualPoseTransformChannel)
        {
            String bone = ((VirtualPoseTransformChannel) sheet.channel).bone;

            createPropertySheet(sheet, bone, "tx", 0);
            createPropertySheet(sheet, bone, "ty", 1);
            createPropertySheet(sheet, bone, "tz", 2);
            createPropertySheet(sheet, bone, "sx", 3);
            createPropertySheet(sheet, bone, "sy", 4);
            createPropertySheet(sheet, bone, "sz", 5);
            createPropertySheet(sheet, bone, "rx", 6);
            createPropertySheet(sheet, bone, "ry", 7);
            createPropertySheet(sheet, bone, "rz", 8);
            createPropertySheet(sheet, bone, "cr", 9);
            createPropertySheet(sheet, bone, "cg", 10);
            createPropertySheet(sheet, bone, "cb", 11);
            createPropertySheet(sheet, bone, "ca", 12);
            createPropertySheet(sheet, bone, "l", 13);
            createPropertySheet(sheet, bone, "fix", 14);
        }
    }

    private static void createBoneSheet(UIKeyframeSheet parent, String bone)
    {
        VirtualPoseTransformChannel channel = new VirtualPoseTransformChannel((KeyframeChannel<Pose>) parent.channel, bone);
        UIKeyframeSheet child = new UIKeyframeSheet(bone, IKey.raw(bone), parent.color, false, channel, null);

        child.parent = parent;
        parent.children.add(child);
    }

    private static void createPropertySheet(UIKeyframeSheet parent, String bone, String prop, int index)
    {
        // Parent of property sheet is still the ROOT Pose Channel (parent.channel.parent in this case?)
        // Wait, parent is Bone Sheet. parent.channel is VirtualPoseTransformChannel.
        // But VirtualKeyframeChannel expects KeyframeChannel<Pose> (or compatible).
        // VirtualPoseTransformChannel.parent IS the Pose Channel.
        
        KeyframeChannel poseChannel = ((VirtualPoseTransformChannel) parent.channel).parent;
        
        VirtualKeyframeChannel channel = new VirtualKeyframeChannel(poseChannel, bone, index);
        UIKeyframeSheet child = new UIKeyframeSheet(prop, IKey.raw(prop), parent.color, false, channel, null);

        child.parent = parent;
        parent.children.add(child);
    }

    public static class VirtualPoseTransformChannel extends KeyframeChannel<PoseTransform>
    {
        public KeyframeChannel<Pose> parent;
        public String bone;

        public VirtualPoseTransformChannel(KeyframeChannel<Pose> parent, String bone)
        {
            super(bone, TRANSFORM_FACTORY);

            this.parent = parent;
            this.bone = bone;

            this.sync();
        }

        public void sync()
        {
            this.list.clear();

            for (Object obj : this.parent.getKeyframes())
            {
                Keyframe<Pose> kf = (Keyframe<Pose>) obj;
                PoseTransform transform = kf.getValue().transforms.get(this.bone);

                if (transform != null)
                {
                    this.list.add(new VirtualPoseTransformKeyframe(kf, this.bone, transform));
                }
            }
        }

        @Override
        public int insert(float tick, PoseTransform value)
        {
            // Insert into parent Pose channel
            // If parent has keyframe, use it.
            int parentIndex = this.parent.insert(tick, this.parent.getFactory().createEmpty());
            Keyframe<Pose> parentKf = (Keyframe<Pose>) this.parent.get(parentIndex);
            
            // Ensure bone exists in Pose
            PoseTransform transform = parentKf.getValue().transforms.get(this.bone);
            
            if (transform == null)
            {
                transform = new PoseTransform();
                // Copy value if provided? 
                if (value != null)
                {
                    transform.lerp(value, 0); // Copy properties
                }
                
                parentKf.getValue().transforms.put(this.bone, transform);
            }
            
            this.sync();
            
            // Find the inserted keyframe in our list
            for (int i = 0; i < this.list.size(); i++)
            {
                if (this.list.get(i).getTick() == tick) return i;
            }
            
            return -1;
        }

        @Override
        public void remove(int index)
        {
            // If we remove a Bone keyframe, we should remove the bone from the Pose?
            VirtualPoseTransformKeyframe kf = (VirtualPoseTransformKeyframe) this.list.get(index);
            
            kf.parent.getValue().transforms.remove(this.bone);
            
            // Do NOT remove the parent Pose keyframe, as it might contain other bones.
            // Just sync.
            this.sync();
        }
    }
    
    public static class VirtualPoseTransformKeyframe extends Keyframe<PoseTransform>
    {
        public Keyframe<Pose> parent;
        public String bone;

        public VirtualPoseTransformKeyframe(Keyframe<Pose> parent, String bone, PoseTransform value)
        {
            super("", TRANSFORM_FACTORY, parent.getTick(), value);
            
            this.parent = parent;
            this.bone = bone;
        }

        @Override
        public void setTick(float tick, boolean dirty)
        {
            super.setTick(tick, dirty);
            this.parent.setTick(tick, dirty);
        }
        
        @Override
        public void setValue(PoseTransform value, boolean dirty)
        {
            // Value is the PoseTransform object itself.
            // If we are setting a NEW instance, we need to put it into the map.
            // But usually we just modify the instance.
            super.setValue(value, dirty);
            
            this.parent.getValue().transforms.put(this.bone, value);
        }
    }

    public static class VirtualKeyframeChannel extends KeyframeChannel<Double>
    {
        private KeyframeChannel<Pose> parent;
        private String bone;
        private int index;

        public VirtualKeyframeChannel(KeyframeChannel<Pose> parent, String bone, int index)
        {
            super(bone + "_" + index, KeyframeFactories.DOUBLE);

            this.parent = parent;
            this.bone = bone;
            this.index = index;

            this.sync();
        }

        public void sync()
            {
                this.list.clear();

                for (Object obj : this.parent.getKeyframes())
                {
                    Keyframe<Pose> kf = (Keyframe<Pose>) obj;
                    PoseTransform transform = kf.getValue().transforms.get(this.bone);

                    if (transform != null)
                    {
                        this.list.add(new VirtualKeyframe(kf, this.bone, this.index));
                    }
                }
            }

            @Override
            public int insert(float tick, Double value)
            {
                int i = super.insert(tick, value);
                Keyframe<Double> kf = (Keyframe<Double>) this.list.get(i);

                // Sync with parent
                // If parent has keyframe at this tick, use it.
                // If not, create one.
                int parentIndex = this.parent.insert(tick, this.parent.getFactory().createEmpty());
                Keyframe<Pose> parentKf = (Keyframe<Pose>) this.parent.get(parentIndex);

                // If we just created a new VirtualKeyframe (not linked), replace it with a linked one
                if (!(kf instanceof VirtualKeyframe))
                {
                    VirtualKeyframe vkf = new VirtualKeyframe(parentKf, this.bone, this.index);
                    if (value != null) vkf.setValue(value);
                    this.list.set(i, vkf);
                }

                return i;
            }

            @Override
        public void remove(int index)
        {
            Keyframe kf = (Keyframe) this.list.get(index);
            super.remove(index);

            if (kf instanceof VirtualKeyframe)
            {
                VirtualKeyframe vkf = (VirtualKeyframe) kf;
                // Remove the bone from the parent Pose keyframe
                vkf.parent.getValue().transforms.remove(this.bone);
            }
        }
    }

    public static class VirtualKeyframe extends Keyframe<Double>
    {
        public Keyframe<Pose> parent;
        public String bone;
        public int index;

        public VirtualKeyframe(Keyframe<Pose> parent, String bone, int index)
        {
            super("", KeyframeFactories.DOUBLE, parent.getTick(), getValue(parent.getValue(), bone, index));

            this.parent = parent;
            this.bone = bone;
            this.index = index;
        }

        private static Double getValue(Pose pose, String bone, int index)
        {
            PoseTransform transform = pose.transforms.get(bone);

            if (transform == null)
            {
                return 0D;
            }

            switch (index)
                {
                    case 0: return (double) transform.translate.x;
                    case 1: return (double) transform.translate.y;
                    case 2: return (double) transform.translate.z;
                    case 3: return (double) transform.scale.x;
                    case 4: return (double) transform.scale.y;
                    case 5: return (double) transform.scale.z;
                    case 6: return (double) transform.rotate.x;
                    case 7: return (double) transform.rotate.y;
                    case 8: return (double) transform.rotate.z;
                    case 9: return (double) transform.color.r;
                    case 10: return (double) transform.color.g;
                    case 11: return (double) transform.color.b;
                    case 12: return (double) transform.color.a;
                    case 13: return (double) transform.lighting;
                    case 14: return (double) transform.fix;
                }

                return 0D;
            }

            @Override
            public void setTick(float tick, boolean dirty)
            {
                super.setTick(tick, dirty);
                this.parent.setTick(tick, dirty);
            }

            @Override
            public void setValue(Double value, boolean dirty)
            {
                super.setValue(value, dirty);

                PoseTransform transform = this.parent.getValue().transforms.get(this.bone);

                if (transform == null)
                {
                    transform = new PoseTransform();
                    this.parent.getValue().transforms.put(this.bone, transform);
                }

                float v = value.floatValue();

                switch (this.index)
                {
                    case 0: transform.translate.x = v; break;
                    case 1: transform.translate.y = v; break;
                    case 2: transform.translate.z = v; break;
                    case 3: transform.scale.x = v; break;
                    case 4: transform.scale.y = v; break;
                    case 5: transform.scale.z = v; break;
                    case 6: transform.rotate.x = v; break;
                    case 7: transform.rotate.y = v; break;
                    case 8: transform.rotate.z = v; break;
                    case 9: transform.color.r = v; break;
                    case 10: transform.color.g = v; break;
                    case 11: transform.color.b = v; break;
                    case 12: transform.color.a = v; break;
                    case 13: transform.lighting = v; break;
                    case 14: transform.fix = v; break;
                }
            }
    }
}
