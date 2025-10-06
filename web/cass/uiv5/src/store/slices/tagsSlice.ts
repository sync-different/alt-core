import { createSlice } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';

export interface Tag {
  tag: string;
  count: number;
}

interface TagsState {
  tags: Tag[];
  loading: boolean;
}

const initialState: TagsState = {
  tags: [],
  loading: false,
};

const tagsSlice = createSlice({
  name: 'tags',
  initialState,
  reducers: {
    setTags: (state, action: PayloadAction<Tag[]>) => {
      state.tags = action.payload;
    },
    setLoading: (state, action: PayloadAction<boolean>) => {
      state.loading = action.payload;
    },
    addTag: (state, action: PayloadAction<Tag>) => {
      const existing = state.tags.find(t => t.tag === action.payload.tag);
      if (!existing) {
        state.tags.push(action.payload);
      }
    },
    removeTag: (state, action: PayloadAction<string>) => {
      state.tags = state.tags.filter(t => t.tag !== action.payload);
    },
  },
});

export const { setTags, setLoading, addTag, removeTag } = tagsSlice.actions;

export default tagsSlice.reducer;
